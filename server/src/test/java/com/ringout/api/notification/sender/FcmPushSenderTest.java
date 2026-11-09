package com.ringout.api.notification.sender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import com.google.api.client.json.gson.GsonFactory;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.SendResponse;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FcmPushSenderTest {

    private static final Instant NOW = Instant.parse("2026-11-09T05:30:00Z");

    private final FirebaseMessaging firebaseMessaging = mock(FirebaseMessaging.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final FcmPushSender fcmPushSender = new FcmPushSender(firebaseMessaging, clock);

    @Test
    void 응답_순서대로_성공_여부를_돌려준다() throws FirebaseMessagingException {
        // given
        BatchResponse response = batchResponse(List.of(successResponse(), failureResponse()));
        given(firebaseMessaging.sendEach(anyList())).willReturn(response);

        // when
        List<Boolean> results = fcmPushSender.send(messages(2));

        // then
        assertThat(results).containsExactly(true, false);
    }

    @Test
    void 한_번에_500건을_넘으면_나눠서_보낸다() throws FirebaseMessagingException {
        // given
        given(firebaseMessaging.sendEach(anyList())).willAnswer(invocation -> {
            List<Message> sent = invocation.getArgument(0);
            return batchResponse(sent.stream().map(message -> successResponse()).toList());
        });

        // when
        List<Boolean> results = fcmPushSender.send(messages(501));

        // then
        assertThat(results).hasSize(501).containsOnly(true);
        then(firebaseMessaging).should(times(2)).sendEach(anyList());
    }

    @Test
    void 일괄_발송_요청이_실패하면_모두_실패로_돌려준다() throws FirebaseMessagingException {
        // given
        given(firebaseMessaging.sendEach(anyList())).willThrow(mock(FirebaseMessagingException.class));

        // when
        List<Boolean> results = fcmPushSender.send(messages(3));

        // then
        assertThat(results).containsExactly(false, false, false);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 즉시_전달되도록_우선순위와_만료_시각을_설정한다() throws FirebaseMessagingException, IOException {
        // given
        ArgumentCaptor<List<Message>> captor = ArgumentCaptor.forClass(List.class);
        BatchResponse response = batchResponse(List.of(successResponse()));
        given(firebaseMessaging.sendEach(captor.capture())).willReturn(response);

        // when
        fcmPushSender.send(messages(1));

        // then
        long expectedExpiration = NOW.plus(Duration.ofMinutes(10)).getEpochSecond();
        String json = GsonFactory.getDefaultInstance().toString(captor.getValue().get(0));
        assertThat(json).contains(
            "\"priority\":\"high\"",
            "\"ttl\":\"600s\"",
            "\"apns-priority\":\"10\"",
            "\"apns-push-type\":\"alert\"",
            "\"apns-expiration\":\"" + expectedExpiration + "\"",
            "\"interruption-level\":\"time-sensitive\""
        );
    }

    private List<PushMessage> messages(int count) {
        return IntStream.range(0, count)
            .mapToObj(i -> new PushMessage("token-" + i, "제목", "본문", null))
            .toList();
    }

    private BatchResponse batchResponse(List<SendResponse> responses) {
        BatchResponse batchResponse = mock(BatchResponse.class);
        given(batchResponse.getResponses()).willReturn(responses);
        return batchResponse;
    }

    private SendResponse successResponse() {
        SendResponse response = mock(SendResponse.class);
        given(response.isSuccessful()).willReturn(true);
        return response;
    }

    private SendResponse failureResponse() {
        SendResponse response = mock(SendResponse.class);
        given(response.isSuccessful()).willReturn(false);
        given(response.getException()).willReturn(mock(FirebaseMessagingException.class));
        return response;
    }
}
