package com.sra.journal_tracking.security;

import com.sra.journal_tracking.entity.jpa.Role;
import com.sra.journal_tracking.entity.jpa.User;
import com.sra.journal_tracking.repository.jpa.RoleRepository;
import com.sra.journal_tracking.repository.jpa.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    @Override
    @Transactional
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        // Auto-downgrade if researcher trial has expired
        // Sử dụng buffer 1 phút để tránh race condition giữa thời điểm set roleExpiryAt và thời điểm kiểm tra
        if (user.getRoleExpiryAt() != null
                && user.getRoleExpiryAt().plusMinutes(1).isBefore(LocalDateTime.now())
                && user.getRole() != null
                && "researcher".equalsIgnoreCase(user.getRole().getRoleName())) {

            Role academicRole = roleRepository.findByRoleNameIgnoreCase("academic_user")
                    .orElse(null);
            if (academicRole != null) {
                log.info("Researcher trial expired for {} (expiry={}, now={}) — downgrading to academic_user",
                        email, user.getRoleExpiryAt(), LocalDateTime.now());
                user.setRole(academicRole);
                user.setRoleExpiryAt(null);
                userRepository.save(user);
            }
        } else if (user.getRoleExpiryAt() != null
                && "researcher".equalsIgnoreCase(user.getRole() != null ? user.getRole().getRoleName() : "")) {
            log.debug("Researcher trial still active for {}: expires at {}, now={}",
                    email, user.getRoleExpiryAt(), LocalDateTime.now());
        }

        return new CustomUserDetails(user);
    }
}
