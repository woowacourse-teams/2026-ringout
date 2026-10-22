package com.ringout.api.notification.repository;

import com.ringout.api.notification.domain.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CampaignRepository extends JpaRepository<Campaign, Long> {
}
