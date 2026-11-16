package com.ringout.api.forest.controller;

import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.config.security.CustomUserDetails;
import com.ringout.api.forest.controller.docs.ForestControllerApi;
import com.ringout.api.forest.dto.request.ForestOnboardingCompleteRequest;
import com.ringout.api.forest.dto.request.ForestTreePlantRequest;
import com.ringout.api.forest.dto.response.ForestOnboardingCompleteResult;
import com.ringout.api.forest.dto.response.ForestOnboardingResponse;
import com.ringout.api.forest.dto.response.ForestResponse;
import com.ringout.api.forest.dto.response.ForestTreePlantResult;
import com.ringout.api.forest.dto.response.ForestTreeResponse;
import com.ringout.api.forest.service.ForestService;
import com.ringout.api.forest.service.ForestTreeService;
import com.ringout.api.forest.status.ForestSuccessStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/forest")
public class ForestController implements ForestControllerApi {

    private final ForestService forestService;
    private final ForestTreeService forestTreeService;

    @Override
    @GetMapping
    public ResponseEntity<CustomResponse<ForestResponse>> findForest(
        @AuthenticationPrincipal CustomUserDetails customUserDetails
    ) {
        ForestResponse response = forestService.getForest(customUserDetails.getUserId());
        return ResponseEntity.status(ForestSuccessStatus.FOREST_FOUND.getHttpStatus())
            .body(CustomResponse.onSuccess(ForestSuccessStatus.FOREST_FOUND, response));
    }

    @Override
    @PutMapping("/onboarding")
    public ResponseEntity<CustomResponse<ForestOnboardingResponse>> completeOnboarding(
        @AuthenticationPrincipal CustomUserDetails customUserDetails,
        @RequestBody(required = false) ForestOnboardingCompleteRequest request
    ) {
        ForestOnboardingCompleteResult result = forestService.completeOnboarding(customUserDetails.getUserId(),
            request);
        ForestSuccessStatus status = result.status();
        return ResponseEntity.status(status.getHttpStatus())
            .body(CustomResponse.onSuccess(status, result.response()));
    }

    @Override
    @PutMapping("/trees/{growthCycle}")
    public ResponseEntity<CustomResponse<ForestTreeResponse>> plantTree(
        @AuthenticationPrincipal CustomUserDetails customUserDetails,
        @PathVariable String growthCycle,
        @RequestBody(required = false) ForestTreePlantRequest request
    ) {
        ForestTreePlantResult result = forestTreeService.plantTree(customUserDetails.getUserId(), growthCycle,
            request);
        ForestSuccessStatus status = result.status();
        return ResponseEntity.status(status.getHttpStatus())
            .body(CustomResponse.onSuccess(status, result.response()));
    }
}
