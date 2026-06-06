package com.sra.journal_tracking.entity.jpa;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

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