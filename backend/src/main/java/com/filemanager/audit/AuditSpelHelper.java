package com.filemanager.audit;

import com.filemanager.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component("auditSpel")
@RequiredArgsConstructor
public class AuditSpelHelper {
    private final UserRepository userRepository;

    public Long currentUserId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || auth.getName() == null) return null;
            return userRepository.findByUsername(auth.getName()).map(u -> u.getId()).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    public Long userIdByUsername(String username) {
        if (username == null) return null;
        try { return userRepository.findByUsername(username).map(u -> u.getId()).orElse(null); } catch (Exception e) { return null; }
    }
}

