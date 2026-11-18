package com.ringout.api.admin.controller;

import com.ringout.api.admin.dto.response.AdminAlarmHistoryResponses.*;
import com.ringout.api.admin.service.AdminAlarmHistoryService;
import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.user.status.UserSuccessStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/alarm-histories")
@Tag(name = "관리자 (ADMIN)")
@SecurityRequirement(name = "Admin Session")
public class AdminAlarmHistoryController {
    private final AdminAlarmHistoryService service;

    @ExceptionHandler(org.springframework.web.method.annotation.HandlerMethodValidationException.class)
    public org.springframework.http.ResponseEntity<CustomResponse<Void>> invalidParameters() {
        return org.springframework.http.ResponseEntity.badRequest()
                .body(CustomResponse.onFailure("COMMON400", "잘못된 요청입니다.", null));
    }

    @GetMapping
    @Operation(summary = "관리자 개인 알람 기록 조회", description = "한국 시간 시작일 기준(date, 기본 오늘). 시작 시간 및 ID 순 정렬(기본 asc). 회원·현재 상태 필터. 이동 시작 후 2분부터 MOVING, 강제 종료는 FORCE_ENDED. 삭제된 기록·탈퇴 회원 제외. page 0부터, size 1~100. 회원 검색은 /admin/users 사용.")
    public CustomResponse<AlarmHistoryPage> getHistories(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @Positive Long userId,
            @RequestParam(required = false) Status status,
            @RequestParam(defaultValue = "asc") @Pattern(regexp = "asc|desc") String sortDirection,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return CustomResponse.onSuccess(UserSuccessStatus.USER_FOUND,
                service.getHistories(date, userId, status, sortDirection, page, size));
    }
}
