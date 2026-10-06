package com.ringout.api.user.repository;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserWithdrawalRepository {

    private final JdbcTemplate jdbcTemplate;

    public List<Long> findOwnedImageFileIds(Long userId) {
        Set<Long> imageFileIds = new LinkedHashSet<>();
        imageFileIds.addAll(jdbcTemplate.queryForList(
            "select image_id from `user` where id = ? and image_id is not null",
            Long.class,
            userId
        ));
        imageFileIds.addAll(jdbcTemplate.queryForList(
            "select image_id from room where host_user_id = ? and image_id is not null",
            Long.class,
            userId
        ));
        return List.copyOf(imageFileIds);
    }

    public List<Long> findOrphanedImageFileIds(List<Long> imageFileIds) {
        return imageFileIds.stream()
            .filter(this::isOrphanedImageFile)
            .toList();
    }

    public void deleteAllByUserId(Long userId) {
        deleteAlarmData(userId);
        deletePushAlarmData(userId);
        deleteRoomData(userId);

        jdbcTemplate.update("delete from user_agreement where user_id = ?", userId);
        jdbcTemplate.update("delete from destination where user_id = ?", userId);
        jdbcTemplate.update("delete from `user` where id = ?", userId);
    }

    private void deleteAlarmData(Long userId) {
        jdbcTemplate.update("""
            delete from alarm_movement
            where alarm_occurrence_id in (
                select id from alarm_occurrence where user_id = ?
            )
            """, userId);
        jdbcTemplate.update("""
            delete from alarm_ringing
            where alarm_occurrence_id in (
                select id from alarm_occurrence where user_id = ?
            )
            """, userId);
        jdbcTemplate.update("delete from alarm_occurrence where user_id = ?", userId);
    }

    private void deletePushAlarmData(Long userId) {
        List<Long> pushAlarmContentIds = jdbcTemplate.queryForList("""
            select distinct push_alarm_content_id
            from push_alarm_record
            where user_id = ?
               or device_id in (select id from device where user_id = ?)
            """, Long.class, userId, userId);
        jdbcTemplate.update("""
            delete from push_alarm_record
            where user_id = ?
               or device_id in (select id from device where user_id = ?)
            """, userId, userId);
        jdbcTemplate.update("delete from device where user_id = ?", userId);
        pushAlarmContentIds.forEach(contentId -> jdbcTemplate.update("""
            delete from push_alarm_content
            where id = ?
              and not exists (
                  select 1 from push_alarm_record where push_alarm_content_id = ?
              )
            """, contentId, contentId));
    }

    private void deleteRoomData(Long userId) {
        jdbcTemplate.update("""
            delete from room_blacklist
            where user_id = ?
               or room_id in (select id from room where host_user_id = ?)
            """, userId, userId);
        jdbcTemplate.update("""
            delete from room_user
            where user_id = ?
               or room_id in (select id from room where host_user_id = ?)
            """, userId, userId);
        jdbcTemplate.update("delete from room where host_user_id = ?", userId);
    }

    private boolean isOrphanedImageFile(Long imageFileId) {
        Integer referenceCount = jdbcTemplate.queryForObject("""
            select
                (select count(*) from `user` where image_id = ?)
              + (select count(*) from room where image_id = ?)
            """, Integer.class, imageFileId, imageFileId);
        return referenceCount != null && referenceCount == 0;
    }
}
