package com.ringout.api.stamp.controller;

import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.common.response.code.status.SuccessStatus;
import com.ringout.api.stamp.controller.docs.StampControllerApi;
import com.ringout.api.stamp.dto.response.CreateGiveUpResponse;
import com.ringout.api.stamp.dto.response.CreateStampResponse;
import com.ringout.api.stamp.dto.response.FindMonthlyStampsResponse;
import com.ringout.api.stamp.service.StampService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
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
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestParam LocalDate completedAt
  ) {
    Long memberId = resolveMemberId(authorization);
    CreateStampResponse response = stampService.createStamp(memberId, completedAt);

    return ResponseEntity.status(SuccessStatus.STAMP_CREATED.getHttpStatus())
        .body(CustomResponse.of(SuccessStatus.STAMP_CREATED, response));
  }

  @PostMapping("/give-ups")
  public ResponseEntity<CustomResponse<CreateGiveUpResponse>> createGiveUp(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestParam LocalDate terminatedAt
  ) {
    Long memberId = resolveMemberId(authorization);
    CreateGiveUpResponse response = stampService.createGiveUp(memberId, terminatedAt);

    return ResponseEntity.status(SuccessStatus.STAMP_GIVE_UP.getHttpStatus())
        .body(CustomResponse.of(SuccessStatus.STAMP_GIVE_UP, response));
  }

  @GetMapping
  public ResponseEntity<CustomResponse<FindMonthlyStampsResponse>> findMonthlyStamps(
      @RequestHeader(value = "Authorization", required = false) String authorization,
      @RequestParam(required = true) Integer year,
      @RequestParam(required = true) Integer month
  ) {
    Long memberId = resolveMemberId(authorization);
    FindMonthlyStampsResponse response = stampService.findMonthlyStamps(memberId, year, month);

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
