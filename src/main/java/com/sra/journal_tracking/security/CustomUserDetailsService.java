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
        if (user.getRoleExpiryAt() != null
                && user.getRoleExpiryAt().isBefore(LocalDateTime.now())
                && user.getRole() != null
                && "researcher".equalsIgnoreCase(user.getRole().getRoleName())) {

            Role academicRole = roleRepository.findByRoleNameIgnoreCase("academic_user")
                    .orElse(null);
            if (academicRole != null) {
                user.setRole(academicRole);
                user.setRoleExpiryAt(null);
                userRepository.save(user);
                log.info("Trial expired for user {} — downgraded to academic_user", email);
            }
        }

        return new CustomUserDetails(user);
    }
}
