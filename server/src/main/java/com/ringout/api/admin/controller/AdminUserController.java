package com.ringout.api.admin.controller;

import com.ringout.api.admin.dto.response.AdminUserResponses.*;
import com.ringout.api.admin.service.AdminUserService;
import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.user.status.UserSuccessStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/users")
@Tag(name = "관리자 (ADMIN)")
@SecurityRequirement(name = "Admin Session")
public class AdminUserController {
    private final AdminUserService service;

    @ExceptionHandler(org.springframework.web.method.annotation.HandlerMethodValidationException.class)
    public org.springframework.http.ResponseEntity<CustomResponse<Void>> invalidParameters() {
        return org.springframework.http.ResponseEntity.badRequest()
                .body(CustomResponse.onFailure("COMMON400", "잘못된 요청입니다.", null));
    }

    @GetMapping
    @Operation(summary = "관리자 회원 목록 조회", description = "닉네임·ID 부분 검색. page는 0부터, size는 1~100. sortBy=id|joinedAt|roomCount, sortDirection=asc|desc(기본 id desc). activeToday 생략 시 전체, true 오늘 활동, false 오늘 비활동. 오늘은 한국 시간 기준. activityDate 지정 시 activeOnDate에 해당 날짜 시작된 개인 알람 기록 존재 여부 제공(로그인·전날 시작된 알람 제외), 생략 시 오늘. activeToday는 항상 오늘 기준. 탈퇴 회원 제외. 이용 제한 필터는 미지원.")
    public CustomResponse<UserPage> getUsers(
            @RequestParam(defaultValue = "") @Size(max = 100) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "id") @jakarta.validation.constraints.Pattern(regexp = "id|joinedAt|roomCount") String sortBy,
            @RequestParam(defaultValue = "desc") @jakarta.validation.constraints.Pattern(regexp = "asc|desc") String sortDirection,
            @RequestParam(required = false) Boolean activeToday,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate activityDate) {
        return CustomResponse.onSuccess(UserSuccessStatus.USER_FOUND, service.getUsers(search, page, size, sortBy, sortDirection, activeToday, activityDate));
    }

    @GetMapping("/stats")
    @Operation(summary = "관리자 회원 통계 조회", description = "한국 시간 오늘 기준. 탈퇴 회원 제외. 전체 회원 수·오늘 가입 회원 수·오늘 활동 회원 수. 활동은 로그인 또는 알람 시작·이동·종료·울림·해제 이벤트 기준이며 회원별 중복 제외. 검색 조건과 무관한 전체 집계.")
    public CustomResponse<UserStats> getStats() {
        return CustomResponse.onSuccess(UserSuccessStatus.USER_FOUND, service.getStats());
    }

    @GetMapping("/{userId}")
    @Operation(summary = "관리자 회원 상세 조회", description = "이메일 마스킹 및 현재 시행 약관 동의 상태 제공. 없는 회원은 404.")
    public CustomResponse<UserDetail> getUser(@PathVariable @Positive Long userId) {
        return CustomResponse.onSuccess(UserSuccessStatus.USER_FOUND, service.getUser(userId));
    }

    @GetMapping("/{userId}/rooms")
    @Operation(summary = "관리자 회원 참여 모임 조회", description = "탈퇴한 참여 관계와 삭제된 모임 제외. 참여 모임이 없으면 빈 배열.")
    public CustomResponse<List<JoinedRoom>> getRooms(@PathVariable @Positive Long userId) {
        return CustomResponse.onSuccess(UserSuccessStatus.USER_FOUND, service.getRooms(userId));
    }
}
