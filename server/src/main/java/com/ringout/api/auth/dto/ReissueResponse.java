package com.ringout.api.auth.dto;

public record ReissueResponse(
    String accessToken
) {

  public static ReissueResponse of(String reissuedAccessToken) {
    return new ReissueResponse(reissuedAccessToken);
  }
}
