package com.ringout.api.stamp.dto.response;

import com.ringout.api.stamp.domain.GoalResult;
import java.time.LocalDate;

public record CreateStampResponse(
    LocalDate completedAt,
    GoalResult goalResult
) {

  public static CreateStampResponse of(LocalDate completedAt, GoalResult goalResult) {
    return new CreateStampResponse(completedAt, goalResult);
  }
}
