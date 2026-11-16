package com.ringout.api.forest.controller;

import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.config.security.CustomUserDetails;
import com.ringout.api.forest.controller.docs.ForestControllerApi;
import com.ringout.api.forest.dto.request.ForestOnboardingCompleteRequest;
import com.ringout.api.forest.dto.response.ForestOnboardingCompleteResult;
import com.ringout.api.forest.dto.response.ForestOnboardingResponse;
import com.ringout.api.forest.service.ForestService;
import com.ringout.api.forest.status.ForestSuccessStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/forest")
public class ForestController implements ForestControllerApi {

    private final ForestService forestService;

    @Override
    @PutMapping("/onboarding")
    public ResponseEntity<CustomResponse<ForestOnboardingResponse>> completeOnboarding(
        @AuthenticationPrincipal CustomUserDetails customUserDetails,
        @RequestBody(required = false) ForestOnboardingCompleteRequest request
    ) {
        ForestOnboardingCompleteResult result = forestService.completeOnboarding(customUserDetails.getUserId(),
            request);
        ForestSuccessStatus status = toOnboardingStatus(result);
        return ResponseEntity.status(status.getHttpStatus())
            .body(CustomResponse.onSuccess(status, result.response()));
    }

    private ForestSuccessStatus toOnboardingStatus(ForestOnboardingCompleteResult result) {
        if (result.created()) {
            return ForestSuccessStatus.FOREST_ONBOARDING_COMPLETED;
        }
        return ForestSuccessStatus.FOREST_ONBOARDING_SYNCED;
    }
}
