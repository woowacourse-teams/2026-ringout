package com.ringout.api.admin.controller;

import com.ringout.api.admin.dto.response.AdminRoomResponses.*;
import com.ringout.api.admin.service.AdminRoomService;
import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.room.status.RecordSuccessStatus;
import com.ringout.api.room.status.RoomSuccessStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/rooms")
@Tag(name = "관리자 (ADMIN)")
@SecurityRequirement(name = "Admin Session")
public class AdminRoomController {
    private final AdminRoomService service;
    private final com.ringout.api.admin.service.AdminRoomTimelineService timelineService;

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<CustomResponse<Void>> invalidParameters() {
        return ResponseEntity.badRequest()
                .body(CustomResponse.onFailure("COMMON400", "잘못된 요청입니다.", null));
    }

    @GetMapping
    @Operation(summary = "관리자 모임 목록 조회", description = "모임 이름·ID·방장 닉네임 부분 검색. page는 0부터, size는 1~100. ID 내림차순. 삭제 모임 제외. 공개·숨김 상태는 미지원.")
    public CustomResponse<RoomPage> getRooms(
            @RequestParam(defaultValue = "") @Size(max = 100) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return CustomResponse.onSuccess(RoomSuccessStatus.ROOM_LIST_FOUND, service.getRooms(search, page, size));
    }

    @GetMapping("/stats")
    @Operation(summary = "관리자 모임 통계 조회", description = "한국 시간 오늘 기준. 삭제 모임 제외. 전체 모임 수·오늘 생성 모임 수·오늘 활동 모임 수. 활동은 latestActivityAt 기준으로 생성·정보 수정·회원 알람/이동 활동을 포함. 검색 조건과 무관한 전체 집계.")
    public CustomResponse<RoomStats> getStats() {
        return CustomResponse.onSuccess(RoomSuccessStatus.ROOM_LIST_FOUND, service.getStats());
    }

    @GetMapping("/{roomId}")
    @Operation(summary = "관리자 모임 상세 조회", description = "소개·사진·방장·멤버 수·일정·생성 시각·최근 활동 제공. 없거나 삭제된 모임은 404.")
    public CustomResponse<RoomDetail> getRoom(@PathVariable @Positive Long roomId) {
        return CustomResponse.onSuccess(RoomSuccessStatus.ROOM_DETAIL_FOUND, service.getRoom(roomId));
    }

    @GetMapping("/{roomId}/timeline")
    @Operation(summary = "관리자 모임 알람 타임라인 조회", description = "현재 참여 회원의 해당 날짜에 시작한 실행을 조회. date 생략 시 한국 시간 오늘. userId 지정 시 한 회원, 생략 시 전체. 실행 단위 page=0, size=50(최대100) 페이징. content의 events를 프론트에서 시간순으로 그린다. hasNext로 다음 실행 페이지 확인. 자정을 넘긴 실행의 이벤트도 포함하며 모임 전용 실행을 의미하지 않는다.")
    public CustomResponse<TimelinePage> getTimeline(
            @PathVariable @Positive Long roomId,
            @RequestParam(required = false) @Positive Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "0") @Min(0) @Max(10000) int page,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
        return CustomResponse.onSuccess(RecordSuccessStatus.ROOM_MEMBER_RECORDS_FOUND,
                timelineService.getTimeline(roomId, userId, date, page, size));
    }

    @GetMapping("/{roomId}/records")
    @Operation(summary = "관리자 모임 멤버 일별 기록 조회", description = "date는 yyyy-MM-dd, 생략 시 한국 시간 오늘. 현재 멤버의 해당 날짜 시작 기록 중 최신 실행(startedAt, ID 순)을 표시. 기록 없는 멤버 포함. 상태 ARRIVED·FORCE_ENDED·UNCONFIRMED·NO_RECORD로 필터 가능. 종료 미확인은 실패나 이동 중으로 단정하지 않음. 요약은 필터 전 전체 멤버 기준. 기록은 모임 전용 실행이 아닌 현재 멤버의 서버 저장 실행 기준.")
    public CustomResponse<Records> getRecords(
            @PathVariable @Positive Long roomId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "") @Size(max = 100) String search,
            @RequestParam(required = false) RecordStatus status) {
        return CustomResponse.onSuccess(RecordSuccessStatus.ROOM_MEMBER_RECORDS_FOUND,
                service.getRecords(roomId, date, search, status));
    }
}
