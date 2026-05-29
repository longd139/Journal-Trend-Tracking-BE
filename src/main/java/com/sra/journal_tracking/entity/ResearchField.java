package com.sra.journal_tracking.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "RESEARCH_FIELD")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"parentField"})
@ToString(exclude = {"parentField"})
public class ResearchField {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "FieldID", updatable = false, nullable = false)
    private UUID fieldId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ParentFieldID")
    private ResearchField parentField;

    @Column(name = "FieldName", nullable = false, length = 200, unique = true)
    private String fieldName;

    @Column(name = "IsTracked", nullable = false)
    @Builder.Default
    private Boolean isTracked = true;

    @Column(name = "Description", length = 500)
    private String description;
}