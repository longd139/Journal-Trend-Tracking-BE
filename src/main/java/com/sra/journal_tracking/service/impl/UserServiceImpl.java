package com.sra.journal_tracking.service.impl;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sra.journal_tracking.dto.user.ChangePasswordRequest;
import com.sra.journal_tracking.dto.user.UpdateProfileRequest;
import com.sra.journal_tracking.dto.user.UserDTO;
import com.sra.journal_tracking.entity.jpa.Role;
import com.sra.journal_tracking.entity.jpa.User;
import com.sra.journal_tracking.exception.AppException;
import com.sra.journal_tracking.exception.ErrorCode;
import com.sra.journal_tracking.repository.jpa.RoleRepository;
import com.sra.journal_tracking.repository.jpa.UserRepository;
import com.sra.journal_tracking.service.UserService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public UserDTO getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        return mapToDTO(user);
    }

    @Override
    @Transactional
    public UserDTO updateProfile(String email, UpdateProfileRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (request.getUsername() != null) user.setFullName(request.getUsername());
        if (request.getOrganization() != null) user.setInstitution(request.getOrganization());

        return mapToDTO(userRepository.save(user));
    }

    @Override
    @Transactional
    public void changePassword(String email, ChangePasswordRequest request) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.INVALID_OLD_PASSWORD);
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    @Override
    @Transactional
    public void upgradeAccount(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        Role researcherRole = roleRepository.findByRoleName("researcher")
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));

        user.setRole(researcherRole);

        userRepository.save(user);
    }

    @Override
    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    @Override
    public UserDTO getUserById(UUID userId) {
        return userRepository.findById(userId)
                .map(this::mapToDTO)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    @Override
    @Transactional
    public UserDTO changeUserStatus(UUID userId, boolean status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        user.setIsActive(status);
        return mapToDTO(userRepository.save(user));
    }

    @Override
    @Transactional
    public UserDTO changeUserRole(UUID userId, String roleName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        Role role = roleRepository.findByRoleName(roleName)
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));
        
        user.setRole(role);

        return mapToDTO(userRepository.save(user));
    }

    private UserDTO mapToDTO(User user) {
        return UserDTO.builder()
                .userId(user.getUserId())
                .username(user.getFullName())
                .email(user.getEmail())
                .organization(user.getInstitution())
                .avatarUrl(null)
                .roleName(user.getRole() != null ? user.getRole().getRoleName() : null)
                .status(user.getIsActive())
                .remainingSearches(null)
                .remainingViews(null)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
