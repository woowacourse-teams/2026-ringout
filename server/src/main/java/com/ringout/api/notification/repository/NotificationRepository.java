package com.ringout.api.notification.repository;

import com.ringout.api.notification.domain.Notification;
import com.ringout.api.notification.domain.NotificationStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query("""
        select notification
        from Notification notification
        join fetch notification.device
        join fetch notification.campaign
        where notification.id in :ids
        """)
    List<Notification> findAllWithDeviceAndCampaignByIdIn(@Param("ids") List<Long> ids);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
        update Notification notification
        set notification.status = :status, notification.updated_at = local datetime
        where notification.id in :ids
        """)
    int updateStatus(@Param("ids") List<Long> ids, @Param("status") NotificationStatus status);
}
