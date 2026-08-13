package com.ringout.api.stamp.dto.response;

import com.ringout.api.stamp.domain.GoalResult;
import java.time.LocalDate;

public record CreateGiveUpResponse(
    LocalDate terminatedAt,
    GoalResult goalResult
) {

  public static CreateGiveUpResponse of(LocalDate terminatedAt, GoalResult goalResult) {
    return new CreateGiveUpResponse(terminatedAt, goalResult);
  }
}
