package com.ringout.api.alarmoccurrence.controller;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ringout.api.alarmoccurrence.domain.OccurrenceEndType;
import com.ringout.api.alarmoccurrence.domain.RingingType;
import com.ringout.api.alarmoccurrence.dto.request.AlarmOccurrenceEventRequest;
import com.ringout.api.alarmoccurrence.dto.request.AlarmOccurrenceStartRequest;
import com.ringout.api.alarmoccurrence.dto.response.AlarmOccurrenceDetailResponse;
import com.ringout.api.alarmoccurrence.dto.response.AlarmOccurrenceResponse;
import com.ringout.api.alarmoccurrence.dto.response.AlarmOccurrenceStartResult;
import com.ringout.api.alarmoccurrence.dto.response.AlarmOccurrenceSummaryResponse;
import com.ringout.api.alarmoccurrence.dto.response.AlarmOccurrencesResponse;
import com.ringout.api.alarmoccurrence.dto.response.AlarmRingingResponse;
import com.ringout.api.alarmoccurrence.service.AlarmOccurrenceService;
import com.ringout.api.config.SecurityConfig;
import com.ringout.api.config.WebConfig;
import com.ringout.api.config.jwt.JwtAuthenticationFilter;
import com.ringout.api.config.jwt.JwtProvider;
import com.ringout.api.config.security.CustomUserDetails;
import com.ringout.api.config.security.JwtAuthenticationEntryPoint;
import com.ringout.api.user.domain.Role;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AlarmOccurrenceController.class)
@Import({WebConfig.class, SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class})
class AlarmOccurrenceControllerTest {

    private static final LocalDate DATE = LocalDate.of(2026, 8, 17);
    private static final String OCCURRENCE_ID = "5c9e1f7a-3b2d-4a6c-8e0f-1a2b3c4d5e6f";
    private static final CustomUserDetails USER = new CustomUserDetails(1L, Role.USER);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AlarmOccurrenceService alarmOccurrenceService;

    @MockitoBean
    private JwtProvider jwtProvider;

    @Nested
    class 개인_알람_사용_기록_조회 {

        @Test
        void 인증된_사용자의_날짜별_알람_사용_기록을_반환한다() throws Exception {
            // given
            AlarmOccurrencesResponse response = new AlarmOccurrencesResponse(
                DATE,
                new AlarmOccurrenceSummaryResponse(1, 2),
                List.of(new AlarmOccurrenceResponse(
                    "alarm-1",
                    OCCURRENCE_ID,
                    LocalTime.of(6, 30),
                    List.of(
                        new AlarmRingingResponse(RingingType.INITIAL, null,
                            OffsetDateTime.parse("2026-08-17T06:30:00+09:00"),
                            OffsetDateTime.parse("2026-08-17T06:31:02+09:00")),
                        new AlarmRingingResponse(RingingType.REPEAT, "E1",
                            OffsetDateTime.parse("2026-08-17T06:35:00+09:00"), null)
                    ),
                    OccurrenceEndType.ARRIVED,
                    OffsetDateTime.parse("2026-08-17T06:53:10+09:00")
                ))
            );
            given(alarmOccurrenceService.getAlarmOccurrences(1L, DATE)).willReturn(response);

            // when
            var result = mockMvc.perform(get("/api/v1/alarm-occurrences")
                .param("date", "2026-08-17")
                .with(user(USER)));

            // then
            result.andExpectAll(
                status().isOk(),
                jsonPath("$.isSuccess").value(true),
                jsonPath("$.code").value("COMMON200"),
                jsonPath("$.message").value("정상적인 요청입니다."),
                jsonPath("$.result.date").value("2026-08-17"),
                jsonPath("$.result.summary.alarmCount").value(1),
                jsonPath("$.result.summary.ringingCount").value(2),
                jsonPath("$.result.occurrences[0].alarmId").value("alarm-1"),
                jsonPath("$.result.occurrences[0].alarmOccurrenceId").value(OCCURRENCE_ID),
                jsonPath("$.result.occurrences[0].alarmTime").value("06:30"),
                jsonPath("$.result.occurrences[0].ringings[0].type").value("INITIAL"),
                jsonPath("$.result.occurrences[0].ringings[0].eventId").isEmpty(),
                jsonPath("$.result.occurrences[0].ringings[0].ringingAt").value("2026-08-17T06:30:00+09:00"),
                jsonPath("$.result.occurrences[0].ringings[0].dismissedAt").value("2026-08-17T06:31:02+09:00"),
                jsonPath("$.result.occurrences[0].ringings[1].eventId").value("E1"),
                jsonPath("$.result.occurrences[0].ringings[1].dismissedAt").isEmpty(),
                jsonPath("$.result.occurrences[0].endType").value("ARRIVED"),
                jsonPath("$.result.occurrences[0].endedAt").value("2026-08-17T06:53:10+09:00")
            );
            verify(alarmOccurrenceService).getAlarmOccurrences(1L, DATE);
        }

        @Test
        void 인증되지_않은_사용자는_알람_사용_기록을_조회할_수_없다() throws Exception {
            // when
            var result = mockMvc.perform(get("/api/v1/alarm-occurrences").param("date", "2026-08-17"));

            // then
            result.andExpect(status().isUnauthorized());
            verifyNoInteractions(alarmOccurrenceService);
        }

        @Test
        void 날짜가_없으면_400을_반환한다() throws Exception {
            // when
            var result = mockMvc.perform(get("/api/v1/alarm-occurrences").with(user(USER)));

            // then
            result.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.code").value("COMMON400")
            );
            verifyNoInteractions(alarmOccurrenceService);
        }

        @Test
        void 날짜_형식이_올바르지_않으면_400을_반환한다() throws Exception {
            // when
            var result = mockMvc.perform(get("/api/v1/alarm-occurrences")
                .param("date", "2026/08/17")
                .with(user(USER)));

            // then
            result.andExpectAll(
                status().isBadRequest(),
                jsonPath("$.code").value("COMMON400")
            );
            verifyNoInteractions(alarmOccurrenceService);
        }
    }

    @Nested
    class 알람_실행_시작 {

        @Test
        void 새_실행이면_201과_발급한_실행_ID를_반환한다() throws Exception {
            // given
            given(alarmOccurrenceService.startAlarmOccurrence(eq(1L), sameStartRequest()))
                .willReturn(new AlarmOccurrenceStartResult(startedDetail(), true));

            // when
            var result = mockMvc.perform(post("/api/v1/alarm-occurrences")
                .with(user(USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"alarmId\":\"alarm-1\",\"scheduledAt\":\"2026-09-23T07:00:00+09:00\"}"));

            // then
            result.andExpectAll(
                status().isCreated(),
                jsonPath("$.isSuccess").value(true),
                jsonPath("$.code").value("COMMON201_1"),
                jsonPath("$.message").value("알람 실행 기록이 저장되었습니다."),
                jsonPath("$.result.alarmOccurrenceId").value(OCCURRENCE_ID),
                jsonPath("$.result.startedAt").value("2026-09-23T07:00:02+09:00"),
                jsonPath("$.result.ringings[0].type").value("INITIAL"),
                jsonPath("$.result.ringings[0].eventId").isEmpty(),
                jsonPath("$.result.ringings[0].dismissedAt").isEmpty(),
                jsonPath("$.result.arrivedAt").isEmpty(),
                jsonPath("$.result.forceEndedAt").isEmpty()
            );
            verify(alarmOccurrenceService).startAlarmOccurrence(eq(1L), sameStartRequest());
        }

        @Test
        void 이미_있는_실행이면_200과_기존_실행을_반환한다() throws Exception {
            // given
            given(alarmOccurrenceService.startAlarmOccurrence(eq(1L), sameStartRequest()))
                .willReturn(new AlarmOccurrenceStartResult(startedDetail(), false));

            // when
            var result = mockMvc.perform(post("/api/v1/alarm-occurrences")
                .with(user(USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"alarmId\":\"alarm-1\",\"scheduledAt\":\"2026-09-23T07:00:00+09:00\"}"));

            // then
            result.andExpectAll(
                status().isOk(),
                jsonPath("$.code").value("COMMON200"),
                jsonPath("$.result.alarmOccurrenceId").value(OCCURRENCE_ID)
            );
        }

        @Test
        void 인증되지_않은_사용자는_실행을_시작할_수_없다() throws Exception {
            // when
            var result = mockMvc.perform(post("/api/v1/alarm-occurrences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"alarmId\":\"alarm-1\",\"scheduledAt\":\"2026-09-23T07:00:00+09:00\"}"));

            // then
            result.andExpect(status().isUnauthorized());
            verifyNoInteractions(alarmOccurrenceService);
        }

        @Test
        void 예정_일시_형식이_올바르지_않으면_400을_반환한다() throws Exception {
            // when
            var result = mockMvc.perform(post("/api/v1/alarm-occurrences")
                .with(user(USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"alarmId\":\"alarm-1\",\"scheduledAt\":\"2026-09-23 07:00\"}"));

            // then
            result.andExpect(status().isBadRequest());
            verifyNoInteractions(alarmOccurrenceService);
        }
    }

    @Nested
    class 알람_실행_이벤트_저장 {

        @Test
        void 이벤트를_병합한_실행_기록을_반환한다() throws Exception {
            // given
            AlarmOccurrenceDetailResponse response = new AlarmOccurrenceDetailResponse(
                OCCURRENCE_ID,
                OffsetDateTime.parse("2026-09-23T07:00:02+09:00"),
                List.of(
                    new AlarmRingingResponse(RingingType.INITIAL, null,
                        OffsetDateTime.parse("2026-09-23T07:00:02+09:00"), null),
                    new AlarmRingingResponse(RingingType.REPEAT, "E1",
                        OffsetDateTime.parse("2026-09-23T07:05:00+09:00"),
                        OffsetDateTime.parse("2026-09-23T07:05:40+09:00"))
                ),
                null,
                null
            );
            given(alarmOccurrenceService.recordAlarmOccurrenceEvent(eq(1L), eq(OCCURRENCE_ID), sameEventRequest()))
                .willReturn(response);

            // when
            var result = mockMvc.perform(patch("/api/v1/alarm-occurrences/" + OCCURRENCE_ID)
                .with(user(USER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"E1\",\"dismissedAt\":\"2026-09-23T07:05:40+09:00\"}"));

            // then
            result.andExpectAll(
                status().isOk(),
                jsonPath("$.code").value("COMMON200"),
                jsonPath("$.result.ringings[1].type").value("REPEAT"),
                jsonPath("$.result.ringings[1].eventId").value("E1"),
                jsonPath("$.result.ringings[1].ringingAt").value("2026-09-23T07:05:00+09:00"),
                jsonPath("$.result.ringings[1].dismissedAt").value("2026-09-23T07:05:40+09:00")
            );
            verify(alarmOccurrenceService).recordAlarmOccurrenceEvent(eq(1L), eq(OCCURRENCE_ID), sameEventRequest());
        }

        @Test
        void 인증되지_않은_사용자는_이벤트를_저장할_수_없다() throws Exception {
            // when
            var result = mockMvc.perform(patch("/api/v1/alarm-occurrences/" + OCCURRENCE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"E1\"}"));

            // then
            result.andExpect(status().isUnauthorized());
            verifyNoInteractions(alarmOccurrenceService);
        }
    }

    // JSON 역직렬화 시 offset이 UTC로 정규화되므로 같은 시점인지로 비교한다.
    private AlarmOccurrenceStartRequest sameStartRequest() {
        return argThat(request -> request.alarmId().equals("alarm-1")
            && request.scheduledAt().isEqual(OffsetDateTime.parse("2026-09-23T07:00:00+09:00")));
    }

    private AlarmOccurrenceEventRequest sameEventRequest() {
        return argThat(request -> "E1".equals(request.eventId())
            && request.dismissedAt().isEqual(OffsetDateTime.parse("2026-09-23T07:05:40+09:00"))
            && request.arrivedAt() == null && request.forceEndedAt() == null);
    }

    private AlarmOccurrenceDetailResponse startedDetail() {
        return new AlarmOccurrenceDetailResponse(
            OCCURRENCE_ID,
            OffsetDateTime.parse("2026-09-23T07:00:02+09:00"),
            List.of(new AlarmRingingResponse(RingingType.INITIAL, null,
                OffsetDateTime.parse("2026-09-23T07:00:02+09:00"), null)),
            null,
            null
        );
    }
}
