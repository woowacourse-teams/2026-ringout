package com.ringout.api.terms.dto;

public record CheckRequiredTermsAgreedResponse(
    boolean agreements
) {

  public static CheckRequiredTermsAgreedResponse of(boolean agreements) {
    return new CheckRequiredTermsAgreedResponse(agreements);
  }
}
