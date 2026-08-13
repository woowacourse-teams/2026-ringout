package com.ringout.api.terms.domain;

import java.util.List;

public enum TermsType {
  SERVICE,
  PRIVACY;

  public static boolean includeAllRequired(List<TermsType> types) {
    return types.containsAll(required());
  }

  public static List<TermsType> required() {
    return List.of(values());
  }


}
