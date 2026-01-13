package com.booster.notificationservice.client;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;

@DisplayName("SlackClient 테스트")
class SlackClientTest {

    @Nested
    @DisplayName("생성자 테스트")
    class Constructor {

        @Test
        @DisplayName("성공: 유효한 webhook URL로 SlackClient를 생성한다")
        void create_success() {
            // given
            String webhookUrl = "https://hooks.slack.com/services/test";

            // when & then
            assertThatCode(() -> new SlackClient(webhookUrl))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("성공: 빈 문자열 webhook URL로도 생성 가능하다")
        void create_with_empty_url() {
            // given
            String webhookUrl = "";

            // when & then
            assertThatCode(() -> new SlackClient(webhookUrl))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("성공: null webhook URL로도 생성 가능하다")
        void create_with_null_url() {
            // given
            String webhookUrl = null;

            // when & then
            assertThatCode(() -> new SlackClient(webhookUrl))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("sendMessage 메서드")
    class SendMessage {

        @Test
        @DisplayName("성공: 유효하지 않은 URL로 전송 시도해도 예외 없이 처리된다 (내부 try-catch)")
        void sendMessage_invalid_url_no_exception() {
            // given
            SlackClient slackClient = new SlackClient("invalid-url");

            // when & then
            assertThatCode(() -> slackClient.sendMessage("테스트 메시지"))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("성공: 빈 메시지도 전송 시도할 수 있다")
        void sendMessage_empty_message() {
            // given
            SlackClient slackClient = new SlackClient("https://hooks.slack.com/test");

            // when & then
            assertThatCode(() -> slackClient.sendMessage(""))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("성공: 긴 메시지도 전송 시도할 수 있다")
        void sendMessage_long_message() {
            // given
            SlackClient slackClient = new SlackClient("https://hooks.slack.com/test");
            String longMessage = "A".repeat(10000);

            // when & then
            assertThatCode(() -> slackClient.sendMessage(longMessage))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("성공: 특수문자가 포함된 메시지도 전송 시도할 수 있다")
        void sendMessage_special_characters() {
            // given
            SlackClient slackClient = new SlackClient("https://hooks.slack.com/test");
            String specialMessage = "한글 메시지 🎉 <script>alert('XSS')</script> \"quotes\" 'single'";

            // when & then
            assertThatCode(() -> slackClient.sendMessage(specialMessage))
                    .doesNotThrowAnyException();
        }
    }
}
