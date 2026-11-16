package com.ringout.api.forest.domain;

import com.ringout.api.common.BaseEntity;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.forest.status.ForestErrorStatus;
import com.ringout.api.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(
    name = "forest_onboarding",
    uniqueConstraints = @UniqueConstraint(name = "uk_forest_onboarding_user", columnNames = "user_id")
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ForestOnboarding extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "completed_at", nullable = false)
    private LocalDateTime completedAt;

    private ForestOnboarding(User user, LocalDateTime completedAt) {
        this.user = user;
        this.completedAt = completedAt;
    }

    public static ForestOnboarding complete(User user, LocalDateTime completedAt) {
        if (completedAt == null) {
            throw new GeneralException(ForestErrorStatus.FOREST_ONBOARDING_REQUEST_INVALID);
        }

        return new ForestOnboarding(user, completedAt.truncatedTo(ChronoUnit.SECONDS));
    }
}
