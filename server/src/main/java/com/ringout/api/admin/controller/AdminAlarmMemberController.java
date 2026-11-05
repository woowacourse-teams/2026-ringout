package com.ringout.api.admin.controller;

import com.ringout.api.admin.service.AdminAlarmMemberService;
import com.ringout.api.admin.service.AdminAlarmMemberService.MemberPage;
import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.user.status.UserSuccessStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/alarm-histories/members")
public class AdminAlarmMemberController {
    private final AdminAlarmMemberService service;

    @ExceptionHandler(org.springframework.web.method.annotation.HandlerMethodValidationException.class)
    public org.springframework.http.ResponseEntity<CustomResponse<Void>> invalidParameters() {
        return org.springframework.http.ResponseEntity.badRequest()
                .body(CustomResponse.onFailure("COMMON400", "잘못된 요청입니다.", null));
    }

    @GetMapping
    public CustomResponse<MemberPage> getMembers(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "") @Size(max = 100) String search,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return CustomResponse.onSuccess(UserSuccessStatus.USER_FOUND, service.getMembers(date, search, page, size));
    }
}
