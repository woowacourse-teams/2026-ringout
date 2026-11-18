package com.ringout.api.notification.repository.dto;

import com.ringout.api.notification.domain.Device;

public record RoomMemberDevice(Long roomId, Device device) {
}
