package com.template.accessories.service;

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

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
public class FileStorageService {

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
     * Stores the uploaded image.
     * Attempts to store on Supabase first; if that fails or is not configured, falls back to local storage.
     *
     * @param file the MultipartFile to store
     * @return public URL of the uploaded image, or null if file is empty
     */
    public String storeFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }

        String rawOriginalName = file.getOriginalFilename();
        String safeName = (rawOriginalName != null ? rawOriginalName : "image.jpg")
                .replaceAll("[^a-zA-Z0-9._-]", "_");
        String uniqueFileName = UUID.randomUUID().toString().substring(0, 8) + "_" + safeName;

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
            Path uploadPath = Paths.get(localUploadDir);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
            }

            Path targetPath = uploadPath.resolve(uniqueFileName);
            try (InputStream in = file.getInputStream(); FileOutputStream out = new FileOutputStream(targetPath.toFile())) {
                in.transferTo(out);
            }

            String localUrl = "/uploads/" + uniqueFileName;
            log.info("Saved image to local storage: {}", localUrl);
            return localUrl;
        } catch (Exception e) {
            log.error("Failed to save image locally", e);
            return null;
        }
    }

    /**
     * Deletes the file from either Supabase or local storage safely.
     *
     * @param fileUrl public URL or path of the file
     */
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isBlank()) {
            return;
        }

        try {
            if (fileUrl.startsWith("/uploads/")) {
                // Local file deletion
                String fileName = fileUrl.substring("/uploads/".length());
                Path filePath = Paths.get(localUploadDir).resolve(fileName);
                Files.deleteIfExists(filePath);
                log.info("Deleted local file: {}", filePath);
            } else if (fileUrl.contains("/storage/v1/object/public/")) {
                // Supabase file deletion
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

    private String extractFileName(String url) {
        if (url == null || url.isBlank()) return null;
        int queryIndex = url.indexOf('?');
        String cleanUrl = queryIndex > 0 ? url.substring(0, queryIndex) : url;
        int lastSlash = cleanUrl.lastIndexOf('/');
        return lastSlash >= 0 ? cleanUrl.substring(lastSlash + 1) : cleanUrl;
    }
}
