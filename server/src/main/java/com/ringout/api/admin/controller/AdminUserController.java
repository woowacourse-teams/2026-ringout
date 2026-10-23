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
import java.util.List;
import lombok.RequiredArgsConstructor;
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
    @Operation(summary = "관리자 회원 목록 조회", description = "닉네임·ID 부분 검색. page는 0부터, size는 1~100. ID 내림차순. 탈퇴 회원 제외. 이용 제한 필터는 미지원.")
    public CustomResponse<UserPage> getUsers(
            @RequestParam(defaultValue = "") @Size(max = 100) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return CustomResponse.onSuccess(UserSuccessStatus.USER_FOUND, service.getUsers(search, page, size));
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
