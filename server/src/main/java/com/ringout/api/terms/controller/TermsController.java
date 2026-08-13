package com.ringout.api.terms.controller;

import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.common.response.code.status.SuccessStatus;
import com.ringout.api.terms.controller.docs.TermsControllerApi;
import com.ringout.api.terms.dto.CheckRequiredTermsAgreedResponse;
import com.ringout.api.terms.dto.TermsAgreeRequest;
import com.ringout.api.terms.dto.TermsAgreeResponse;
import com.ringout.api.terms.service.TermsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/terms")
public class TermsController implements TermsControllerApi {

  private final TermsService termsService;

  @PostMapping
  public ResponseEntity<CustomResponse<TermsAgreeResponse>> termsAgree(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @Valid @RequestBody TermsAgreeRequest termsAgreeRequest) {

    Long memberId = resolveMemberId(authorization);
    TermsAgreeResponse response = termsService.termsAgree(memberId, termsAgreeRequest);

    return ResponseEntity.status(SuccessStatus.TERMS_AGREED.getHttpStatus())
        .body(CustomResponse.of(SuccessStatus.TERMS_AGREED, response));
  }

  @GetMapping("/agreements/me")
  public ResponseEntity<CustomResponse<CheckRequiredTermsAgreedResponse>> checkRequiredTermsAgreed(
      @RequestHeader(value = "Authorization", required = false) String authorization
  ) {
    Long memberId = resolveMemberId(authorization);
    CheckRequiredTermsAgreedResponse response = termsService.checkRequiredTermsAgreed(memberId);

    return ResponseEntity.status(SuccessStatus._OK.getHttpStatus())
        .body(CustomResponse.of(SuccessStatus._OK, response));
  }

  /*
   * TODO: 인증/인가 기능이 별도로 구현되면 실제 토큰 검증 로직으로 대체되어야 합니다.
   * 현재는 Authorization 헤더의 값을 그대로 memberId로 취급하는 임시 처리입니다.
   */
  private Long resolveMemberId(String authorization) {
    String token = authorization.startsWith("Bearer ") ? authorization.substring(7) : authorization;
    return Long.valueOf(token);
  }
}
