package com.ringout.api.admin.controller;

import com.ringout.api.common.response.CustomResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.server.ResponseStatusException;
import com.ringout.api.deployment.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/deployments")
@RequiredArgsConstructor
@Tag(name = "관리자 (ADMIN)")
@SecurityRequirement(name = "Admin Session")
public class AdminDeploymentController {
    private final DeploymentService service;
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<CustomResponse<Void>> invalidParameters() {
        return ResponseEntity.badRequest()
                .body(CustomResponse.onFailure("COMMON400", "잘못된 요청입니다.", null));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<CustomResponse<Void>> failure(
            ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode())
                .body(CustomResponse.onFailure("DEPLOYMENT" + e.getStatusCode().value(), e.getReason(), null));
    }

    public record Summary(Long id, String deploymentId, String environment, String image,
            String imageId, String imageDigest, String previousImage, String previousImageId,
            String previousImageDigest, DeploymentRequest.Status status, Instant startedAt, Instant finishedAt,
            String actionType, Long rollbackSourceId, Long actionsRunId, String flywayVersion) {
        static Summary from(Deployment d) {
            var r = d.toRequest();
            return new Summary(d.getId(), r.deploymentId(), r.environment(), r.image(), r.imageId(),
                    d.getImageDigest(), r.previousImage(), r.previousImageId(), d.getPreviousImageDigest(), r.status(), r.startedAt(), r.finishedAt(),
                    d.getActionType(), d.getRollbackSourceId(), d.getActionsRunId(), d.getFlywayVersion());
        }
    }
    public record DeploymentPage(List<Summary> content, int page, int size,
            long totalElements, int totalPages, Long latestSuccessfulDeploymentId) {}
    public record Detail(Summary deployment, String events) {}


    private <T> CustomResponse<T> ok(T result) {
        return new CustomResponse<>(true, "COMMON200", "성공입니다.", result);
    }

    @GetMapping
    @Operation(summary = "배포 이력 조회", description = "환경 필수. 종료 시각·ID 내림차순. 시각은 UTC.")
    public CustomResponse<DeploymentPage> list(
            @RequestParam @Pattern(regexp = "[a-z][a-z0-9-]{0,31}") String environment,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var result = service.list(environment, page, size);
        var latest = service.latestSuccess(environment);
        return ok(new DeploymentPage(result.getContent().stream().map(Summary::from).toList(),
                page, size, result.getTotalElements(), result.getTotalPages(),
                latest == null ? null : latest.getId()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "배포 상세 및 로그 조회")
    public CustomResponse<Detail> detail(@PathVariable @Positive long id) {
        var d = service.detail(id);
        return ok(new Detail(Summary.from(d), d.getEvents()));
    }

}
