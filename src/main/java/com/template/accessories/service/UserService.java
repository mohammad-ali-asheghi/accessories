package com.template.accessories.service;

import com.template.accessories.entity.UserEntity;
import com.template.accessories.repository.UserRepository;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public @NonNull UserEntity loadUserByUsername(@NonNull String username) throws UsernameNotFoundException {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("username not found: " + username));
    }

    public UserEntity findByUsernameOrChatId(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new UsernameNotFoundException("شناسه کاربری مشخص نشده است.");
        }
        String clean = identifier.trim();
        Optional<UserEntity> user = userRepository.findByUsername(clean);
        if (user.isPresent()) {
            return user.get();
        }
        try {
            Long chatId = Long.parseLong(clean);
            return userRepository.findByChatId(chatId)
                    .orElseThrow(() -> new UsernameNotFoundException("کاربری با شناسه " + clean + " یافت نشد!"));
        } catch (NumberFormatException e) {
            throw new UsernameNotFoundException("کاربری با شناسه " + clean + " یافت نشد!");
        }
    }
}
