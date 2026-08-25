package com.ringout.api.user.controller;

import com.ringout.api.common.response.CustomResponse;
import com.ringout.api.config.security.CustomUserDetails;
import com.ringout.api.user.controller.docs.UserControllerApi;
import com.ringout.api.user.dto.request.UpdateNicknameRequest;
import com.ringout.api.user.dto.response.UpdateNicknameResponse;
import com.ringout.api.user.dto.response.UserResponse;
import com.ringout.api.user.service.UserService;
import com.ringout.api.user.status.UserSuccessStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/users")
public class UserController implements UserControllerApi {

    private final UserService userService;

    @Override
    @GetMapping("/me")
    public ResponseEntity<CustomResponse<UserResponse>> getUser(
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        UserResponse response = userService.getUser(userDetails.getUserId());

        return ResponseEntity.status(UserSuccessStatus.USER_FOUND.getHttpStatus())
            .body(CustomResponse.onSuccess(UserSuccessStatus.USER_FOUND, response));
    }

    @Override
    @PatchMapping("/me/nickname")
    public ResponseEntity<CustomResponse<UpdateNicknameResponse>> updateNickname(
        @AuthenticationPrincipal CustomUserDetails userDetails,
        @Valid @RequestBody UpdateNicknameRequest request
    ) {
        UpdateNicknameResponse response = userService.updateNickname(
            userDetails.getUserId(),
            request
        );

        return ResponseEntity.status(UserSuccessStatus.USER_UPDATED.getHttpStatus())
            .body(CustomResponse.onSuccess(UserSuccessStatus.USER_UPDATED, response));
    }

    @Override
    @DeleteMapping("/me")
    public ResponseEntity<CustomResponse<Void>> withdraw(
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        userService.withdraw(userDetails.getUserId());

        return ResponseEntity.status(UserSuccessStatus.USER_WITHDRAWN.getHttpStatus())
            .body(CustomResponse.onSuccess(UserSuccessStatus.USER_WITHDRAWN, null));
    }
}
