package com.booster.ddayservice.auth.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GoogleOAuthClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("authorization code로 access token을 교환한다")
    void should_exchangeToken_when_validCode() throws JsonProcessingException {
        // given
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        GoogleTokenResponse expected = new GoogleTokenResponse("access-token-123", "Bearer", 3600, "id-token-456");
        server.expect(requestTo("https://oauth2.googleapis.com/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(objectMapper.writeValueAsString(expected), MediaType.APPLICATION_JSON));

        GoogleOAuthClient client = new GoogleOAuthClient(builder, "client-id", "client-secret");

        // when
        GoogleTokenResponse result = client.exchangeToken("auth-code", "http://localhost:3000/callback");

        // then
        assertThat(result.accessToken()).isEqualTo("access-token-123");
        assertThat(result.tokenType()).isEqualTo("Bearer");
        server.verify();
    }

    @Test
    @DisplayName("access token으로 사용자 정보를 조회한다")
    void should_getUserInfo_when_validAccessToken() throws JsonProcessingException {
        // given
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        GoogleUserInfoResponse expected = new GoogleUserInfoResponse("sub-123", "test@gmail.com", "홍길동", "https://photo.url");
        server.expect(requestTo("https://www.googleapis.com/oauth2/v3/userinfo"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer access-token"))
                .andRespond(withSuccess(objectMapper.writeValueAsString(expected), MediaType.APPLICATION_JSON));

        GoogleOAuthClient client = new GoogleOAuthClient(builder, "client-id", "client-secret");

        // when
        GoogleUserInfoResponse result = client.getUserInfo("access-token");

        // then
        assertThat(result.sub()).isEqualTo("sub-123");
        assertThat(result.email()).isEqualTo("test@gmail.com");
        assertThat(result.name()).isEqualTo("홍길동");
        assertThat(result.picture()).isEqualTo("https://photo.url");
        server.verify();
    }
}
