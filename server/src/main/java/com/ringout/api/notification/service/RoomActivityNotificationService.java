package com.ringout.api.notification.service;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.mapping;
import static java.util.stream.Collectors.toList;

import com.ringout.api.notification.domain.Campaign;
import com.ringout.api.notification.domain.CampaignTargetType;
import com.ringout.api.notification.domain.Device;
import com.ringout.api.notification.domain.Notification;
import com.ringout.api.notification.domain.TargetType;
import com.ringout.api.notification.repository.CampaignRepository;
import com.ringout.api.notification.repository.DeviceTokenRepository;
import com.ringout.api.notification.repository.NotificationRepository;
import com.ringout.api.notification.repository.dto.RoomMemberDevice;
import com.ringout.api.room.domain.ActivityDay;
import com.ringout.api.room.domain.Room;
import com.ringout.api.room.repository.RoomRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoomActivityNotificationService {

    private static final String BODY = "활동 시간이에요! 지금 참여해보세요.";

    private final RoomRepository roomRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final CampaignRepository campaignRepository;
    private final NotificationRepository notificationRepository;

    @Transactional
    public List<Long> create(LocalDateTime now) {
        int dayMask = ActivityDay.from(now.getDayOfWeek()).getMask();
        List<Room> rooms = roomRepository.findActiveByActivityDayAndTime(
            dayMask, now.getHour(), now.getMinute());
        if (rooms.isEmpty()) {
            return List.of();
        }

        Map<Long, List<Device>> devicesByRoomId = findDevicesByRoomId(rooms);

        List<Notification> notifications = new ArrayList<>();
        for (Room room : rooms) {
            List<Device> devices = devicesByRoomId.getOrDefault(room.getId(), List.of());
            if (devices.isEmpty()) {
                continue;
            }
            Campaign campaign = campaignRepository.save(
                Campaign.of(room.getName(), BODY, null, CampaignTargetType.ROOM_MEMBER));
            devices.forEach(device -> notifications.add(
                Notification.of(device.getUser(), device, campaign, TargetType.ROOM)));
        }

        notificationRepository.saveAll(notifications);
        log.info("방 활동 알림 생성 - 방 {}개, 알림 {}건", rooms.size(), notifications.size());
        return notifications.stream()
            .map(Notification::getId)
            .toList();
    }

    private Map<Long, List<Device>> findDevicesByRoomId(List<Room> rooms) {
        List<Long> roomIds = rooms.stream()
            .map(Room::getId)
            .toList();
        return deviceTokenRepository.findActiveRoomMemberDevices(roomIds).stream()
            .collect(groupingBy(RoomMemberDevice::roomId,
                mapping(RoomMemberDevice::device, toList())));
    }
}
