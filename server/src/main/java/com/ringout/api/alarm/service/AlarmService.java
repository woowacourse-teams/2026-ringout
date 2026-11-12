package com.ringout.api.alarm.service;

import com.ringout.api.alarm.domain.Alarm;
import com.ringout.api.alarm.domain.AlarmSetting;
import com.ringout.api.alarm.domain.AlarmVersion;
import com.ringout.api.alarm.domain.ClientAlarmId;
import com.ringout.api.alarm.dto.request.AlarmUpsertRequest;
import com.ringout.api.alarm.dto.response.AlarmListResponse;
import com.ringout.api.alarm.dto.response.AlarmResponse;
import com.ringout.api.alarm.repository.AlarmRepository;
import com.ringout.api.alarm.status.AlarmErrorStatus;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AlarmService {

    private final AlarmRepository alarmRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public AlarmListResponse getAlarms(Long userId) {
        validateAuthenticatedUserExists(userId);

        List<AlarmResponse> alarms = alarmRepository.findOwnedAlarms(userId).stream()
            .map(AlarmResponse::from)
            .toList();
        return new AlarmListResponse(alarms);
    }

    @Transactional
    public AlarmResponse upsertAlarm(Long userId, String alarmId, AlarmUpsertRequest request) {
        validateAuthenticatedUserExists(userId);
        ClientAlarmId clientAlarmId = ClientAlarmId.from(alarmId);
        validateAlarmUpsertRequestBodyExists(request);

        AlarmSetting setting = AlarmSetting.of(request.time(), request.dayOfWeek(), request.repeatTime(),
            request.destinationName(), request.destinationLatitude(), request.destinationLongitude());
        AlarmVersion version = AlarmVersion.from(request.alarmVersion());

        alarmRepository.upsertOwnedAlarm(userId, clientAlarmId.getValue(), setting.alarmTime(), setting.daysMask(),
            setting.repeatMinutes(), setting.destinationName(), setting.latitude(), setting.longitude(),
            version.getValue(), LocalDateTime.now(clock));

        return AlarmResponse.from(findUpsertedAlarm(userId, clientAlarmId));
    }

    @Transactional
    public void deleteAlarm(Long userId, String alarmId) {
        validateAuthenticatedUserExists(userId);
        validateAlarmIdToDeleteIsUsable(alarmId);

        alarmRepository.deleteOwnedAlarm(userId, alarmId);
    }

    private void validateAuthenticatedUserExists(Long userId) {
        if (userId == null || !userRepository.existsById(userId)) {
            throw new GeneralException(AlarmErrorStatus.ALARM_UNAUTHORIZED);
        }
    }

    private void validateAlarmIdToDeleteIsUsable(String alarmId) {
        if (!ClientAlarmId.isValid(alarmId)) {
            throw new GeneralException(AlarmErrorStatus.ALARM_DELETE_ID_INVALID);
        }
    }

    private void validateAlarmUpsertRequestBodyExists(AlarmUpsertRequest request) {
        if (request == null) {
            throw new GeneralException(AlarmErrorStatus.ALARM_REQUEST_INVALID);
        }
    }

    private Alarm findUpsertedAlarm(Long userId, ClientAlarmId clientAlarmId) {
        return alarmRepository.findOwnedAlarmForUpdate(userId, clientAlarmId.getValue())
            .orElseThrow(() -> new IllegalStateException(
                "Upsert한 알람을 찾을 수 없습니다. userId=" + userId + ", alarmId=" + clientAlarmId.getValue()));
    }
}
