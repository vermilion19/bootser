package com.booster.ddayservice.member.event;

import com.booster.core.web.event.MemberEvent;
import com.booster.ddayservice.member.application.MemberSyncService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MemberEventConsumerTest {

    @InjectMocks
    private MemberEventConsumer memberEventConsumer;

    @Mock
    private MemberSyncService memberSyncService;

    @Mock
    private ObjectMapper objectMapper;

    @Nested
    @DisplayName("consume")
    class Consume {

        @Test
        @DisplayName("유효한 JSON payload 수신 시 MemberSyncService.process()를 호출한다")
        void should_callProcess_when_validPayload() {
            String payload = """
                    {"eventId":"uuid-1","memberId":1,"nickName":"testUser","timestamp":"2026-01-01T00:00:00","eventType":"SIGNUP"}
                    """;
            MemberEvent expectedEvent = MemberEvent.of(1L, "testUser", MemberEvent.EventType.SIGNUP);
            given(objectMapper.readValue(payload, MemberEvent.class)).willReturn(expectedEvent);

            memberEventConsumer.consume(payload);

            ArgumentCaptor<MemberEvent> captor = ArgumentCaptor.forClass(MemberEvent.class);
            verify(memberSyncService).process(captor.capture());
            assertThat(captor.getValue().memberId()).isEqualTo(1L);
            assertThat(captor.getValue().eventType()).isEqualTo(MemberEvent.EventType.SIGNUP);
        }

        @Test
        @DisplayName("잘못된 JSON payload 수신 시 예외가 발생한다")
        void should_throwException_when_invalidPayload() {
            String invalidPayload = "invalid-json";
            given(objectMapper.readValue(invalidPayload, MemberEvent.class))
                    .willThrow(new RuntimeException("Failed to parse JSON"));

            assertThatThrownBy(() -> memberEventConsumer.consume(invalidPayload))
                    .isInstanceOf(RuntimeException.class);

            verify(memberSyncService, never()).process(any());
        }
    }
}
