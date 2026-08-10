package com.ringout.api.destination.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class DestinationAlias {

  private static final int MAX_LENGTH = 12;

  @Column(name = "alias", nullable = false, length = MAX_LENGTH)
  private String value;
}
