package com.ringout.api.member.dto.request;

import com.ringout.api.member.domain.Nickname;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateNicknameRequest(
    @NotBlank(message = "닉네임은 비어 있을 수 없습니다.")
    @Size(max = Nickname.MAX_LENGTH, message = "닉네임은 최대 10자입니다.")
    @Pattern(
        regexp = "^[가-힣A-Za-z0-9]+$",
        message = "닉네임은 한글, 영문, 숫자만 사용할 수 있습니다."
    )
    String nickname
) {
}
