package com.ringout.api.notification.repository;

import com.ringout.api.notification.domain.Notification;
import com.ringout.api.notification.domain.NotificationStatus;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
        update Notification notification
        set notification.status = com.ringout.api.notification.domain.NotificationStatus.SENDING,
            notification.claimToken = :claimToken,
            notification.claimedAt = :now,
            notification.updated_at = :now
        where notification.id in :ids
          and (notification.status = com.ringout.api.notification.domain.NotificationStatus.PENDING
               or (notification.status = com.ringout.api.notification.domain.NotificationStatus.SENDING
                   and notification.claimedAt < :leaseExpiredBefore))
        """)
    int claim(@Param("ids") List<Long> ids, @Param("claimToken") String claimToken,
        @Param("now") LocalDateTime now, @Param("leaseExpiredBefore") LocalDateTime leaseExpiredBefore);

    @Query("""
        select notification
        from Notification notification
        join fetch notification.device
        join fetch notification.campaign
        where notification.claimToken = :claimToken
        """)
    List<Notification> findAllWithDeviceAndCampaignByClaimToken(@Param("claimToken") String claimToken);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
        update Notification notification
        set notification.status = :status, notification.updated_at = local datetime
        where notification.id in :ids
          and notification.claimToken = :claimToken
        """)
    int updateStatus(@Param("ids") List<Long> ids, @Param("status") NotificationStatus status,
        @Param("claimToken") String claimToken);

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("""
        update Notification notification
        set notification.status = com.ringout.api.notification.domain.NotificationStatus.EXPIRED,
            notification.updated_at = local datetime
        where notification.status in (com.ringout.api.notification.domain.NotificationStatus.PENDING,
                                      com.ringout.api.notification.domain.NotificationStatus.SENDING)
          and notification.created_at < :expiredBefore
        """)
    int expire(@Param("expiredBefore") LocalDateTime expiredBefore);

    @Query("""
        select notification.id
        from Notification notification
        where notification.created_at >= :recoverableAfter
          and ((notification.status = com.ringout.api.notification.domain.NotificationStatus.PENDING
                and notification.created_at < :pendingBefore)
            or (notification.status = com.ringout.api.notification.domain.NotificationStatus.SENDING
                and notification.claimedAt < :leaseExpiredBefore))
        order by notification.id
        """)
    List<Long> findRecoverableIds(@Param("recoverableAfter") LocalDateTime recoverableAfter,
        @Param("pendingBefore") LocalDateTime pendingBefore,
        @Param("leaseExpiredBefore") LocalDateTime leaseExpiredBefore, Limit limit);
}
