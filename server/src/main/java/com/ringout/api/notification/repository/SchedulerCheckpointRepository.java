package com.ringout.api.notification.repository;

import com.ringout.api.notification.domain.SchedulerCheckpoint;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchedulerCheckpointRepository extends JpaRepository<SchedulerCheckpoint, String> {
}
