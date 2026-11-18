package com.ringout.api.deployment;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** EC2 배포 기록 수집 전용 API. X-Deployment-Token으로 인증한다. */
@RestController
@RequestMapping("/internal/deployments")
@RequiredArgsConstructor
public class DeploymentIngestController {
    private final DeploymentService service;
    private final FlywaySchemaState schema;

    @GetMapping("/schema")
    public FlywaySchemaState.Snapshot schema() { return schema.current(); }

    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public ResponseEntity<Void> failure(
            org.springframework.web.server.ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).build();
    }

    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<Void> concurrentDelivery() {
        // A concurrent insert can win the unique key; the outbox retries afterwards.
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void record(@RequestBody @Valid DeploymentRequest request) {
        service.record(request);
    }
}
