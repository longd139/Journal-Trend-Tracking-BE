package com.sra.journal_tracking.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "ROLE")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Role {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "RoleID", updatable = false, nullable = false)
    private UUID roleId;

    @Column(name = "RoleName", nullable = false, length = 50, unique = true)
    private String roleName;

    @Column(name = "Description", length = 500)
    private String description;
}