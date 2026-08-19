package com.ringout.api.auth.dto;

public record ReissueResponse(
    String accessToken,
    String refreshToken
) {

  public static ReissueResponse of(String reissuedAccessToken, String reissuedRefreshToken) {
    return new ReissueResponse(reissuedAccessToken, reissuedRefreshToken);
  }
}
