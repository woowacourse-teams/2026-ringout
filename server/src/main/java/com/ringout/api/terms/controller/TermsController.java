package com.ringout.api.terms.controller;

import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.config.security.CustomUserDetails;
import com.ringout.api.terms.controller.docs.TermsControllerApi;
import com.ringout.api.terms.dto.request.TermsAgreeRequest;
import com.ringout.api.terms.dto.response.CheckRequiredTermsAgreedResponse;
import com.ringout.api.terms.dto.response.TermsAgreeResponse;
import com.ringout.api.terms.service.TermsService;
import com.ringout.api.terms.status.TermsSuccessStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/terms")
public class TermsController implements TermsControllerApi {

    private final TermsService termsService;

    @PostMapping
    public ResponseEntity<CustomResponse<TermsAgreeResponse>> termsAgree(
        @AuthenticationPrincipal CustomUserDetails customUserDetails,
        @Valid @RequestBody TermsAgreeRequest termsAgreeRequest) {

        TermsAgreeResponse response = termsService.termsAgree(customUserDetails.getUserId(), termsAgreeRequest);

        return ResponseEntity.status(TermsSuccessStatus.TERMS_AGREED.getHttpStatus())
            .body(CustomResponse.onSuccess(TermsSuccessStatus.TERMS_AGREED, response));
    }

    @GetMapping("/agreements/me")
    public ResponseEntity<CustomResponse<CheckRequiredTermsAgreedResponse>> checkRequiredTermsAgreed(
        @AuthenticationPrincipal CustomUserDetails customUserDetails
    ) {
        CheckRequiredTermsAgreedResponse response =
            termsService.checkRequiredTermsAgreed(customUserDetails.getUserId());

        return ResponseEntity.status(
                TermsSuccessStatus.REQUIRED_TERMS_AGREEMENT_CHECKED.getHttpStatus())
            .body(CustomResponse.onSuccess(
                TermsSuccessStatus.REQUIRED_TERMS_AGREEMENT_CHECKED,
                response
            ));
    }
}
