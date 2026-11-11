package com.ringout.api.alarm.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ringout.api.alarm.domain.AlarmDay;
import com.ringout.api.alarm.dto.request.AlarmUpsertRequest;
import com.ringout.api.alarm.dto.response.AlarmListResponse;
import com.ringout.api.alarm.dto.response.AlarmResponse;
import com.ringout.api.alarm.service.AlarmService;
import com.ringout.api.alarm.status.AlarmErrorStatus;
import com.ringout.api.common.response.error.GeneralException;
import com.ringout.api.config.SecurityConfig;
import com.ringout.api.config.WebConfig;
import com.ringout.api.config.jwt.JwtAuthenticationFilter;
import com.ringout.api.config.jwt.JwtProvider;
import com.ringout.api.config.security.CustomUserDetails;
import com.ringout.api.config.security.JwtAuthenticationEntryPoint;
import com.ringout.api.user.domain.Role;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AlarmController.class)
@Import({WebConfig.class, SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class AlarmControllerTest {

    private static final String UPSERT_URL = "/api/v1/alarms/alarm-001";
    private static final String REQUEST_BODY = """
        {"time":"07:00","dayOfWeek":["MONDAY","FRIDAY"],"repeatTime":5,"destinationName":"세종대학교",
        "destinationLatitude":37.5502,"destinationLongitude":127.0738,"alarmVersion":1}
        """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AlarmService alarmService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @Nested
    class 알람_목록_조회_응답 {

        @Test
        void 알람_목록을_alarms_배열로_반환한다() throws Exception {
            // given
            given(alarmService.getAlarms(1L)).willReturn(new AlarmListResponse(List.of(
                new AlarmResponse("alarm-001", LocalTime.of(7, 0), List.of(AlarmDay.MONDAY, AlarmDay.FRIDAY), 5,
                    "세종대학교", 37.5502, 127.0738, 1L),
                new AlarmResponse("alarm-002", LocalTime.of(8, 30), List.of(), 10, "서울역", 37.5547, 126.9706,
                    3L)
            )));

            // when
            var result = mockMvc.perform(get("/api/v1/alarms")
                .with(user(new CustomUserDetails(1L, Role.USER))));

            // then
            result.andExpectAll(
                status().isOk(),
                jsonPath("$.code").value("ALARM200"),
                jsonPath("$.message").value("전체 알람 조회에 성공했습니다."),
                jsonPath("$.result.alarms.length()").value(2),
                jsonPath("$.result.alarms[0].alarmId").value("alarm-001"),
                jsonPath("$.result.alarms[0].time").value("07:00"),
                jsonPath("$.result.alarms[0].dayOfWeek", contains("MONDAY", "FRIDAY")),
                jsonPath("$.result.alarms[0].repeatTime").value(5),
                jsonPath("$.result.alarms[0].destinationName").value("세종대학교"),
                jsonPath("$.result.alarms[0].destinationLatitude").value(37.5502),
                jsonPath("$.result.alarms[0].destinationLongitude").value(127.0738),
                jsonPath("$.result.alarms[0].alarmVersion").value(1),
                jsonPath("$.result.alarms[1].alarmId").value("alarm-002"),
                jsonPath("$.result.alarms[1].time").value("08:30"),
                jsonPath("$.result.alarms[1].dayOfWeek").isEmpty(),
                jsonPath("$.result.alarms[1].alarmVersion").value(3)
            );
        }

        @Test
        void 알람이_없으면_빈_alarms_배열을_반환한다() throws Exception {
            // given
            given(alarmService.getAlarms(1L)).willReturn(new AlarmListResponse(List.of()));

            // when
            var result = mockMvc.perform(get("/api/v1/alarms")
                .with(user(new CustomUserDetails(1L, Role.USER))));

            // then
            result.andExpectAll(
                status().isOk(),
                jsonPath("$.code").value("ALARM200"),
                jsonPath("$.result.alarms").isArray(),
                jsonPath("$.result.alarms").isEmpty()
            );
        }

        @Test
        void 인증_정보가_없으면_AUTH401을_반환한다() throws Exception {
            // given

            // when
            var result = mockMvc.perform(get("/api/v1/alarms"));

            // then
            result.andExpectAll(
                status().isUnauthorized(),
                jsonPath("$.code").value("AUTH401")
            );
            verify(alarmService, never()).getAlarms(any());
        }
    }

    @Nested
    class 알람_upsert_응답 {

        @Test
        void 경로의_알람_ID와_body로_upsert하고_200과_서버에_저장된_알람을_반환한다() throws Exception {
            // given
            given(alarmService.upsertAlarm(any(), any(), any())).willReturn(new AlarmResponse("alarm-001",
                LocalTime.of(9, 0), List.of(AlarmDay.MONDAY, AlarmDay.TUESDAY, AlarmDay.FRIDAY), 5, "세종대학교",
                37.5502, 127.0738, 3L));

            // when
            var result = mockMvc.perform(put(UPSERT_URL)
                .with(user(new CustomUserDetails(1L, Role.USER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST_BODY));

            // then
            result.andExpectAll(
                status().isOk(),
                jsonPath("$.code").value("ALARM200"),
                jsonPath("$.message").value("알람 동기화에 성공했습니다."),
                jsonPath("$.result.alarmId").value("alarm-001"),
                jsonPath("$.result.time").value("09:00"),
                jsonPath("$.result.dayOfWeek", contains("MONDAY", "TUESDAY", "FRIDAY")),
                jsonPath("$.result.repeatTime").value(5),
                jsonPath("$.result.destinationName").value("세종대학교"),
                jsonPath("$.result.destinationLatitude").value(37.5502),
                jsonPath("$.result.destinationLongitude").value(127.0738),
                jsonPath("$.result.alarmVersion").value(3)
            );
            verify(alarmService).upsertAlarm(1L, "alarm-001", new AlarmUpsertRequest(LocalTime.of(7, 0),
                List.of(AlarmDay.MONDAY, AlarmDay.FRIDAY), 5, "세종대학교", 37.5502, 127.0738, 1L));
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "{\"time\":\"7:00\",\"dayOfWeek\":[],\"repeatTime\":5,\"destinationName\":\"a\",\"destinationLatitude\":1,\"destinationLongitude\":1,\"alarmVersion\":1}",
            "{\"time\":\"25:00\",\"dayOfWeek\":[],\"repeatTime\":5,\"destinationName\":\"a\",\"destinationLatitude\":1,\"destinationLongitude\":1,\"alarmVersion\":1}",
            "{\"time\":\"07:00\",\"dayOfWeek\":[\"MON\"],\"repeatTime\":5,\"destinationName\":\"a\",\"destinationLatitude\":1,\"destinationLongitude\":1,\"alarmVersion\":1}",
            "{\"time\":\"07:00\",\"dayOfWeek\":[],\"repeatTime\":5,\"destinationName\":\"a\",\"destinationLatitude\":1,\"destinationLongitude\":1,\"alarmVersion\":\"abc\"}",
            "{broken"
        })
        void 요청_본문을_바인딩할_수_없으면_COMMON400을_반환한다(String body) throws Exception {
            // given

            // when
            var result = mockMvc.perform(put(UPSERT_URL)
                .with(user(new CustomUserDetails(1L, Role.USER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));

            // then
            result.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.code").value("COMMON400")
            );
            verify(alarmService, never()).upsertAlarm(any(), any(), any());
        }

        @Test
        void 서비스_검증에_실패하면_ALARM400을_반환한다() throws Exception {
            // given
            given(alarmService.upsertAlarm(any(), any(), any()))
                .willThrow(new GeneralException(AlarmErrorStatus.ALARM_REQUEST_INVALID));

            // when
            var result = mockMvc.perform(put(UPSERT_URL)
                .with(user(new CustomUserDetails(1L, Role.USER)))
                .contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST_BODY));

            // then
            result.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.code").value("ALARM400"),
                jsonPath("$.message").value("알람 요청 정보가 올바르지 않습니다."),
                jsonPath("$.result").value(nullValue())
            );
        }

        @Test
        void 인증_정보가_없으면_AUTH401을_반환한다() throws Exception {
            // given

            // when
            var result = mockMvc.perform(put(UPSERT_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(REQUEST_BODY));

            // then
            result.andExpectAll(
                status().isUnauthorized(),
                jsonPath("$.code").value("AUTH401")
            );
            verify(alarmService, never()).upsertAlarm(any(), any(), any());
        }
    }
}
