package com.ringout.api.deployment;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "deployments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Deployment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 36)
    private String deploymentId;
    @Column(nullable = false, length = 32)
    private String environment;
    @Column(nullable = false, length = 512)
    private String image;
    @Column(length = 100)
    private String imageId;
    @Column(length = 512)
    private String previousImage;
    @Column(length = 100)
    private String previousImageId;
    @Column(length = 512)
    private String imageDigest;
    @Column(length = 512)
    private String previousImageDigest;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private DeploymentRequest.Status status;
    @Column(nullable = false)
    private LocalDateTime startedAt;
    @Column(nullable = false)
    private LocalDateTime finishedAt;
    @Column(nullable = false, columnDefinition = "MEDIUMTEXT")
    private String events;

    @Column(nullable = false, length = 20)
    private String actionType;
    @Column
    private Long rollbackSourceId;
    private Long actionsRunId;

    @Column(length = 100)
    private String flywayVersion;
    @Column(length = 64)
    private String flywayFingerprint;

    public Deployment(DeploymentRequest request) {
        flywayVersion = request.flywayVersion();
        flywayFingerprint = request.flywayFingerprint();
        actionsRunId = request.actionsRunId();
        actionType = request.rollbackSourceId() == null ? "DEPLOY" : "ROLLBACK";
        rollbackSourceId = request.rollbackSourceId();
        deploymentId = request.deploymentId();
        environment = request.environment();
        image = request.image();
        imageId = request.imageId();
        previousImage = request.previousImage();
        previousImageId = request.previousImageId();
        imageDigest = request.imageDigest();
        previousImageDigest = request.previousImageDigest();
        status = request.status();
        startedAt = LocalDateTime.ofInstant(request.startedAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS), ZoneOffset.UTC);
        finishedAt = LocalDateTime.ofInstant(request.finishedAt().truncatedTo(java.time.temporal.ChronoUnit.MICROS), ZoneOffset.UTC);
        events = request.events();
    }

    public DeploymentRequest toRequest() {
        return new DeploymentRequest(deploymentId, environment, image, imageId, previousImage,
                previousImageId, status, startedAt.toInstant(ZoneOffset.UTC),
                finishedAt.toInstant(ZoneOffset.UTC), events, rollbackSourceId, null, flywayVersion, flywayFingerprint,
                imageDigest, previousImageDigest);
    }
}
