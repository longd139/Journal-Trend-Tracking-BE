package com.sra.journal_tracking.entity.jpa;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "DASHBOARD_WIDGET")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"user"})
@ToString(exclude = {"user"})
public class DashboardWidget {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "WidgetID", updatable = false, nullable = false)
    private UUID widgetId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "UserID", nullable = false)
    private User user;

    @Column(name = "WidgetType", nullable = false, length = 30)
    private String widgetType;

    @Column(name = "Title", nullable = false, length = 200)
    private String title;

    @Column(name = "Config", columnDefinition = "NVARCHAR(MAX)")
    private String config;

    @Column(name = "PositionX", nullable = false)
    @Builder.Default
    private Integer positionX = 0;

    @Column(name = "PositionY", nullable = false)
    @Builder.Default
    private Integer positionY = 0;

    @Column(name = "Width", nullable = false)
    @Builder.Default
    private Integer width = 4;

    @Column(name = "Height", nullable = false)
    @Builder.Default
    private Integer height = 3;

    @Column(name = "CreatedAt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "UpdatedAt", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
