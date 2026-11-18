package com.ringout.api.deployment;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeploymentService {
    private final DeploymentRepository repository;
    @Transactional
    public void record(DeploymentRequest request) {
        var existing = repository.findByDeploymentId(request.deploymentId());
        var deployment = new Deployment(request);
        if (existing.isPresent()) {
            var saved = existing.get();
            if (!saved.toRequest().equals(deployment.toRequest())
                    || !java.util.Objects.equals(saved.getActionsRunId(), deployment.getActionsRunId()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Deployment ID already has different data");
            return;
        }
        if (request.rollbackSourceId() != null) {
            var target = detail(request.rollbackSourceId());
            if (!target.getEnvironment().equals(request.environment())
                    || target.getStatus() != DeploymentRequest.Status.SUCCEEDED
                    || (request.status() == DeploymentRequest.Status.SUCCEEDED
                        && (!java.util.Objects.equals(target.getImageId(), request.imageId())
                            || !java.util.Objects.equals(target.getImageDigest(), request.imageDigest()))))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Rollback record does not match target deployment");
        }
        repository.saveAndFlush(deployment);
    }

    public Page<Deployment> list(String environment, int page, int size) {
        return repository.findByEnvironment(environment, PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "finishedAt", "id")));
    }

    public Deployment detail(long id) {
        return repository.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Deployment not found"));
    }

    public Deployment latestSuccess(String environment) {
        return repository.findFirstByEnvironmentAndStatusOrderByFinishedAtDescIdDesc(
                environment, DeploymentRequest.Status.SUCCEEDED).orElse(null);
    }
}
