package org.abelsromero.springapp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class EchoControllerTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void should_return_message() {
        final String message = "Hello world!";

        var response = restTemplate
            .exchange("/echo/%s".formatted(message), HttpMethod.GET, headers(), Map.class);

        assertThat(response.getStatusCode())
            .isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
            .containsExactly(Map.entry("message", message));
    }

    @Test
    void should_fail_when_message_is_missing() {
        var response = restTemplate
            .exchange("/echo/", HttpMethod.GET, headers(), Map.class);

        assertThat(response.getStatusCode())
            .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private HttpEntity headers() {
        var headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(headers);
    }
}
