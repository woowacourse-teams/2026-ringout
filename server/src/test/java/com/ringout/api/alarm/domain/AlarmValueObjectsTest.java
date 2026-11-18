package com.ringout.api.alarm.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ringout.api.alarm.status.AlarmErrorStatus;
import com.ringout.api.common.response.error.GeneralException;
import java.time.LocalTime;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class AlarmValueObjectsTest {

    @Nested
    class 알람_ID_규칙 {

        @Test
        void 알람_ID는_36자까지_허용한다() {
            // given
            String value = "123456789012345678901234567890123456";

            // when
            ClientAlarmId clientAlarmId = ClientAlarmId.from(value);

            // then
            assertThat(clientAlarmId.getValue()).isEqualTo(value);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", " ", " alarm", "alarm ", "alarm 1", "1234567890123456789012345678901234567"})
        void 알람_ID가_비어있거나_공백을_포함하거나_36자를_넘으면_생성할_수_없다(String value) {
            // given

            // when // then
            assertThatThrownBy(() -> ClientAlarmId.from(value))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(AlarmErrorStatus.ALARM_ID_INVALID));
        }
    }

    @Nested
    class 알람_시간_규칙 {

        @Test
        void 알람_시간을_생성한다() {
            // given
            LocalTime value = LocalTime.of(7, 0);

            // when
            AlarmTime alarmTime = AlarmTime.from(value);

            // then
            assertThat(alarmTime.getValue()).isEqualTo(value);
        }

        @Test
        void 알람_시간이_없으면_생성할_수_없다() {
            // given
            LocalTime value = null;

            // when // then
            assertThatThrownBy(() -> AlarmTime.from(value))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(AlarmErrorStatus.ALARM_REQUEST_INVALID));
        }
    }

    @Nested
    class 반복_시간_규칙 {

        @ParameterizedTest
        @ValueSource(ints = {0, 5, 30})
        void 반복_시간은_0분부터_30분까지_허용한다(int value) {
            // given

            // when
            RepeatMinutes repeatMinutes = RepeatMinutes.from(value);

            // then
            assertThat(repeatMinutes.getValue()).isEqualTo(value);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(ints = {-1, 31})
        void 반복_시간이_없거나_0분부터_30분_범위를_벗어나면_생성할_수_없다(Integer value) {
            // given

            // when // then
            assertThatThrownBy(() -> RepeatMinutes.from(value))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(AlarmErrorStatus.ALARM_REQUEST_INVALID));
        }
    }

    @Nested
    class 목적지_이름_규칙 {

        @Test
        void 목적지_이름은_20자까지_허용한다() {
            // given
            String value = "12345678901234567890";

            // when
            DestinationName destinationName = DestinationName.from(value);

            // then
            assertThat(destinationName.getValue()).isEqualTo(value);
        }

        @Test
        void 목적지_이름의_앞뒤_공백은_제거한다() {
            // given
            String value = "  세종대학교  ";

            // when
            DestinationName destinationName = DestinationName.from(value);

            // then
            assertThat(destinationName.getValue()).isEqualTo("세종대학교");
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(strings = {"", "   ", "123456789012345678901"})
        void 목적지_이름이_비어있거나_20자를_넘으면_생성할_수_없다(String value) {
            // given

            // when // then
            assertThatThrownBy(() -> DestinationName.from(value))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(AlarmErrorStatus.ALARM_REQUEST_INVALID));
        }
    }

    @Nested
    class 목적지_좌표_규칙 {

        @ParameterizedTest
        @ValueSource(doubles = {-90.0, 0.0, 37.5502, 90.0})
        void 위도는_마이너스_90부터_90까지_허용한다(double value) {
            // given

            // when
            Latitude latitude = Latitude.from(value);

            // then
            assertThat(latitude.getValue()).isEqualTo(value);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(doubles = {-90.1, 90.1, Double.NaN, Double.POSITIVE_INFINITY})
        void 위도가_없거나_범위를_벗어나면_생성할_수_없다(Double value) {
            // given

            // when // then
            assertThatThrownBy(() -> Latitude.from(value))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(AlarmErrorStatus.ALARM_REQUEST_INVALID));
        }

        @ParameterizedTest
        @ValueSource(doubles = {-180.0, 0.0, 127.0738, 180.0})
        void 경도는_마이너스_180부터_180까지_허용한다(double value) {
            // given

            // when
            Longitude longitude = Longitude.from(value);

            // then
            assertThat(longitude.getValue()).isEqualTo(value);
        }

        @ParameterizedTest
        @NullSource
        @ValueSource(doubles = {-180.1, 180.1, Double.NaN, Double.NEGATIVE_INFINITY})
        void 경도가_없거나_범위를_벗어나면_생성할_수_없다(Double value) {
            // given

            // when // then
            assertThatThrownBy(() -> Longitude.from(value))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(AlarmErrorStatus.ALARM_REQUEST_INVALID));
        }
    }

    @Nested
    class 알람_버전_규칙 {

        @Test
        void 알람_버전을_그대로_보관한다() {
            // given
            Long value = 3L;

            // when
            AlarmVersion alarmVersion = AlarmVersion.from(value);

            // then
            assertThat(alarmVersion.getValue()).isEqualTo(3L);
        }

        @Test
        void 알람_버전이_없으면_생성할_수_없다() {
            // given
            Long value = null;

            // when // then
            assertThatThrownBy(() -> AlarmVersion.from(value))
                .isInstanceOfSatisfying(GeneralException.class, exception ->
                    assertThat(exception.getCode()).isEqualTo(AlarmErrorStatus.ALARM_VERSION_INVALID));
        }
    }
}
