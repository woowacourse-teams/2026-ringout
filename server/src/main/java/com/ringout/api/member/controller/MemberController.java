package com.ringout.api.member.controller;

import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.config.security.CustomUserDetails;
import com.ringout.api.member.controller.docs.MemberControllerApi;
import com.ringout.api.member.dto.request.UpdateNicknameRequest;
import com.ringout.api.member.dto.response.UpdateNicknameResponse;
import com.ringout.api.member.service.MemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class MemberController implements MemberControllerApi {

  private final MemberService memberService;

  @Override
  @PatchMapping("/me/nickname")
  public CustomResponse<UpdateNicknameResponse> updateNickname(
      @AuthenticationPrincipal CustomUserDetails userDetails,
      @Valid @RequestBody UpdateNicknameRequest request
  ) {
    UpdateNicknameResponse response = memberService.updateNickname(
        userDetails.getUserId(),
        request
    );

    return CustomResponse.onSuccess(response);
  }

  @Override
  @DeleteMapping("/me")
  public CustomResponse<Void> withdraw(
      @AuthenticationPrincipal CustomUserDetails userDetails
  ) {
    memberService.withdraw(userDetails.getUserId());

    return CustomResponse.onSuccess(null);
  }
}
