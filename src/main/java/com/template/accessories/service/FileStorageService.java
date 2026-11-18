package com.template.accessories.service;

import com.template.accessories.exception.ServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp",
            "image/gif"
    );

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".webp", ".gif"
    );

    @Value("${supabase.url:}")
    private String supabaseUrl;

    @Value("${supabase.key:}")
    private String supabaseKey;

    @Value("${app.upload.dir:/tmp/accessories-uploads}")
    private String localUploadDir;

    private final RestTemplate restTemplate;

    public FileStorageService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Stores multiple uploaded image files and returns their public URLs.
     */
    public List<String> storeFiles(List<MultipartFile> files) {
        List<String> urls = new ArrayList<>();
        if (files == null || files.isEmpty()) {
            return urls;
        }
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                String url = storeFile(file);
                if (url != null && !url.isBlank()) {
                    urls.add(url);
                }
            }
        }
        return urls;
    }

    /**
     * Validates and stores a single uploaded image.
     * Attempts Supabase Storage first if configured; falls back to local disk storage.
     */
    public String storeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        validateImageFile(file);

        String rawOriginalName = file.getOriginalFilename();
        String safeName = (rawOriginalName != null ? rawOriginalName : "image.jpg")
                .replaceAll("[^a-zA-Z0-9._-]", "_");
        if (safeName.length() > 80) {
            safeName = safeName.substring(safeName.length() - 80);
        }
        String uniqueFileName = UUID.randomUUID().toString().replace("-", "").substring(0, 12) + "_" + safeName;

        // Try Supabase first if configured
        if (supabaseUrl != null && !supabaseUrl.isBlank() && supabaseKey != null && !supabaseKey.isBlank()) {
            try {
                String bucketName = "images";
                String uploadEndpoint = supabaseUrl.replaceAll("/+$", "") + "/storage/v1/object/" + bucketName + "/" + uniqueFileName;

                HttpHeaders headers = new HttpHeaders();
                headers.setBearerAuth(supabaseKey);
                headers.set("apikey", supabaseKey);

                String contentType = file.getContentType();
                if (contentType == null || contentType.isBlank()) {
                    contentType = MediaType.IMAGE_JPEG_VALUE;
                }
                headers.setContentType(MediaType.parseMediaType(contentType));

                HttpEntity<byte[]> requestEntity = new HttpEntity<>(file.getBytes(), headers);
                ResponseEntity<String> response = restTemplate.exchange(
                        uploadEndpoint,
                        HttpMethod.POST,
                        requestEntity,
                        String.class
                );

                if (response.getStatusCode().is2xxSuccessful()) {
                    String publicUrl = supabaseUrl.replaceAll("/+$", "") + "/storage/v1/object/public/" + bucketName + "/" + uniqueFileName;
                    log.info("Uploaded image to Supabase storage: {}", publicUrl);
                    return publicUrl;
                } else {
                    log.warn("Supabase upload returned non-2xx: {}", response.getStatusCode());
                }
            } catch (Exception e) {
                log.warn("Failed to upload to Supabase: {}. Falling back to local disk storage.", e.getMessage());
            }
        }

        // Fallback: save to local disk
        try {
            Path uploadPath = Paths.get(localUploadDir).toAbsolutePath().normalize();
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            Path targetPath = uploadPath.resolve(uniqueFileName).normalize();
            if (!targetPath.startsWith(uploadPath)) {
                throw new ServiceException("مسیر فایل نامعتبر است.");
            }

            try (InputStream in = file.getInputStream(); FileOutputStream out = new FileOutputStream(targetPath.toFile())) {
                in.transferTo(out);
            }

            String localUrl = "/uploads/" + uniqueFileName;
            log.info("Saved image to local storage: {}", localUrl);
            return localUrl;
        } catch (ServiceException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to save image locally", e);
            throw new ServiceException("خطا در ذخیره فایل تصویر روی سرور.");
        }
    }

    /**
     * Deletes the file from either Supabase or local storage safely (with path traversal protection).
     */
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank() || "/icon.png".equals(fileUrl)) {
            return;
        }

        try {
            if (fileUrl.startsWith("/uploads/")) {
                String fileName = fileUrl.substring("/uploads/".length());
                Path baseDir = Paths.get(localUploadDir).toAbsolutePath().normalize();
                Path filePath = baseDir.resolve(fileName).normalize();
                if (!filePath.startsWith(baseDir)) {
                    log.warn("Blocked path traversal attempt on delete: {}", fileUrl);
                    return;
                }
                Files.deleteIfExists(filePath);
                log.info("Deleted local file: {}", filePath);
            } else if (fileUrl.contains("/storage/v1/object/public/")
                    && supabaseUrl != null && !supabaseUrl.isBlank()
                    && supabaseKey != null && !supabaseKey.isBlank()) {
                String fileName = extractFileName(fileUrl);
                if (fileName != null && !fileName.isBlank()) {
                    String bucketName = "images";
                    String deleteEndpoint = supabaseUrl.replaceAll("/+$", "") + "/storage/v1/object/" + bucketName + "/" + fileName;

                    HttpHeaders headers = new HttpHeaders();
                    headers.setBearerAuth(supabaseKey);
                    headers.set("apikey", supabaseKey);

                    HttpEntity<String> entity = new HttpEntity<>(headers);
                    restTemplate.exchange(deleteEndpoint, HttpMethod.DELETE, entity, String.class);
                    log.info("Deleted file from Supabase storage: {}", fileName);
                }
            }
        } catch (Exception e) {
            log.warn("Failed to delete file from storage: {}", e.getMessage());
        }
    }

    private void validateImageFile(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new ServiceException("فرمت فایل مجاز نیست. فقط تصاویر JPG، PNG، WebP و GIF قابل آپلود هستند.");
        }

        String originalName = file.getOriginalFilename();
        if (originalName != null && !originalName.isBlank()) {
            String lower = originalName.toLowerCase(Locale.ROOT);
            boolean validExt = ALLOWED_EXTENSIONS.stream().anyMatch(lower::endsWith);
            if (!validExt) {
                throw new ServiceException("پسوند فایل تصویر معتبر نیست.");
            }
        }
    }

    private String extractFileName(String url) {
        if (url == null || url.isBlank()) return null;
        int queryIndex = url.indexOf('?');
        String cleanUrl = queryIndex > 0 ? url.substring(0, queryIndex) : url;
        int lastSlash = cleanUrl.lastIndexOf('/');
        return lastSlash >= 0 ? cleanUrl.substring(lastSlash + 1) : cleanUrl;
    }
}
