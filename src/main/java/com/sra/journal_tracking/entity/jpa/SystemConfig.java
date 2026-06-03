package com.sra.journal_tracking.entity.jpa;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "SYSTEM_CONFIG")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "ConfigID", updatable = false, nullable = false)
    private UUID configId;

    @Column(name = "ConfigKey", nullable = false, unique = true)
    private String configKey;

    @Column(name = "ConfigValue", nullable = false)
    private String configValue;
}