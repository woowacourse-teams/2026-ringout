package com.ringout.api.admin.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "admin_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "google_subject", nullable = false, unique = true, length = 255)
    private String googleSubject;
    @Column(nullable = false, length = 320)
    private String email;
    // Google profile name is optional; it is never used for authorization.
    @Column(length = 255)
    private String name;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdminStatus status;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public static AdminAccount pending(String subject, String email, String name) {
        AdminAccount account = new AdminAccount();
        account.googleSubject = subject;
        account.email = email;
        account.name = normalizeName(name);
        account.status = AdminStatus.PENDING;
        account.createdAt = LocalDateTime.now();
        account.updatedAt = account.createdAt;
        return account;
    }

    public void updateProfile(String email, String name) {
        this.email = email;
        String normalizedName = normalizeName(name);
        if (normalizedName != null) this.name = normalizedName;
        this.updatedAt = LocalDateTime.now();
    }

    private static String normalizeName(String name) {
        if (name == null || name.isBlank()) return null;
        String normalized = name.strip();
        return normalized.length() > 255 ? normalized.substring(0, 255) : normalized;
    }

    public boolean isApproved() { return status == AdminStatus.APPROVED; }
}
