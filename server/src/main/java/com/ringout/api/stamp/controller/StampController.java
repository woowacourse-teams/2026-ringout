package com.ringout.api.stamp.controller;

import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.config.security.CustomUserDetails;
import com.ringout.api.stamp.controller.docs.StampControllerApi;
import com.ringout.api.stamp.dto.response.CreateGiveUpResponse;
import com.ringout.api.stamp.dto.response.CreateStampResponse;
import com.ringout.api.stamp.dto.response.FindMonthlyStampsResponse;
import com.ringout.api.stamp.service.StampService;
import com.ringout.api.stamp.status.StampSuccessStatus;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/stamp")
public class StampController implements StampControllerApi {

    private final StampService stampService;

    @PostMapping("/success")
    public ResponseEntity<CustomResponse<CreateStampResponse>> createStamp(
        @AuthenticationPrincipal CustomUserDetails customUserDetails,
        @RequestParam LocalDate completedAt
    ) {
        CreateStampResponse response = stampService.createStamp(customUserDetails.getUserId(), completedAt);

        return ResponseEntity.status(StampSuccessStatus.STAMP_CREATED.getHttpStatus())
            .body(CustomResponse.onSuccess(StampSuccessStatus.STAMP_CREATED, response));
    }

    @PostMapping("/give-ups")
    public ResponseEntity<CustomResponse<CreateGiveUpResponse>> createGiveUp(
        @AuthenticationPrincipal CustomUserDetails customUserDetails,
        @RequestParam LocalDate terminatedAt
    ) {
        CreateGiveUpResponse response = stampService.createGiveUp(customUserDetails.getUserId(), terminatedAt);

        return ResponseEntity.status(StampSuccessStatus.STAMP_GIVE_UP.getHttpStatus())
            .body(CustomResponse.onSuccess(StampSuccessStatus.STAMP_GIVE_UP, response));
    }

    @GetMapping
    public ResponseEntity<CustomResponse<FindMonthlyStampsResponse>> findMonthlyStamps(
        @AuthenticationPrincipal CustomUserDetails customUserDetails,
        @RequestParam(required = true) Integer year,
        @RequestParam(required = true) Integer month
    ) {
        FindMonthlyStampsResponse response =
            stampService.findMonthlyStamps(customUserDetails.getUserId(), year, month);

        return ResponseEntity.status(StampSuccessStatus.MONTHLY_STAMPS_FOUND.getHttpStatus())
            .body(CustomResponse.onSuccess(StampSuccessStatus.MONTHLY_STAMPS_FOUND, response));
    }
}
