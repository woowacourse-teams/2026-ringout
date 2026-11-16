package com.ringout.api.config.converter;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import org.springframework.core.convert.converter.Converter;

public class YearMonthConverter implements Converter<String, YearMonth> {

    private static final DateTimeFormatter YEAR_MONTH_FORMATTER = new DateTimeFormatterBuilder()
        .appendValue(ChronoField.YEAR, 4)
        .appendLiteral('-')
        .appendValue(ChronoField.MONTH_OF_YEAR, 2)
        .toFormatter()
        .withResolverStyle(ResolverStyle.STRICT);

    @Override
    public YearMonth convert(String source) {
        return YearMonth.parse(source, YEAR_MONTH_FORMATTER);
    }
}
