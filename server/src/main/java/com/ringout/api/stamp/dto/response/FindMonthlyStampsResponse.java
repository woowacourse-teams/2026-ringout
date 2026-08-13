package com.ringout.api.stamp.dto.response;

import java.time.LocalDate;
import java.util.List;

public record FindMonthlyStampsResponse(
    Integer year,
    Integer month,
    List<LocalDate> successDates
) {

  public static FindMonthlyStampsResponse of(Integer year, Integer month, List<LocalDate> successDates) {
    return new FindMonthlyStampsResponse(year, month, successDates);
  }
}
