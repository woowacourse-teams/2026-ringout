package com.ringout.api.deployment;

import jakarta.validation.constraints.*;
import java.time.Instant;

public record DeploymentRequest(
        @NotBlank @Pattern(regexp = "[0-9a-fA-F-]{36}") String deploymentId,
        @NotBlank @Pattern(regexp = "[a-z][a-z0-9-]{0,31}") String environment,
        @NotBlank @Size(max = 512) String image,
        @Size(max = 100) String imageId,
        @Size(max = 512) String previousImage,
        @Size(max = 100) String previousImageId,
        @NotNull Status status,
        @NotNull Instant startedAt,
        @NotNull Instant finishedAt,
        @NotBlank @Size(max = 65535) String events,
        @Positive Long rollbackSourceId,
        @Positive Long actionsRunId,
        @Size(max = 100) String flywayVersion,
        @Pattern(regexp = "[0-9a-f]{64}") String flywayFingerprint,
        @Size(max = 512) @Pattern(regexp = "[^\\s@]+@sha256:[0-9a-f]{64}") String imageDigest,
        @Size(max = 512) @Pattern(regexp = "[^\\s@]+@sha256:[0-9a-f]{64}") String previousImageDigest) {
    public DeploymentRequest(String deploymentId, String environment, String image, String imageId,
            String previousImage, String previousImageId, Status status, Instant startedAt,
            Instant finishedAt, String events, Long rollbackSourceId, Long actionsRunId) {
        this(deploymentId, environment, image, imageId, previousImage, previousImageId, status,
                startedAt, finishedAt, events, rollbackSourceId, actionsRunId, null, null, null, null);
    }
    public DeploymentRequest(String deploymentId, String environment, String image, String imageId,
            String previousImage, String previousImageId, Status status, Instant startedAt,
            Instant finishedAt, String events) {
        this(deploymentId, environment, image, imageId, previousImage, previousImageId, status,
                startedAt, finishedAt, events, null, null);
    }
    public enum Status { SUCCEEDED, FAILED }

    @AssertTrue(message = "finishedAt must be on or after startedAt")
    public boolean isTimeRangeValid() {
        return startedAt == null || finishedAt == null || !finishedAt.isBefore(startedAt);
    }
}
