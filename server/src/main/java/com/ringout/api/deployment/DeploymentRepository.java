package com.ringout.api.deployment;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeploymentRepository extends JpaRepository<Deployment, Long> {
    Optional<Deployment> findByDeploymentId(String deploymentId);
    Page<Deployment> findByEnvironment(String environment, Pageable pageable);
    Optional<Deployment> findFirstByEnvironmentAndStatusOrderByFinishedAtDescIdDesc(
            String environment, DeploymentRequest.Status status);
}
