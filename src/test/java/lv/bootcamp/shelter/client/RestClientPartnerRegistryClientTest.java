package lv.bootcamp.shelter.client;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestClientPartnerRegistryClientTest {

    private MockWebServer server;
    private RestClientPartnerRegistryClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        String baseUrl = "http://" + server.getHostName() + ":" + server.getPort();
        client = new RestClientPartnerRegistryClient(RestClient.builder(), baseUrl);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void lookup_shouldReturnResultWhenPartnerRespondsSuccessfully() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""
                        {"registered": true, "partnerShelterName": "Riga Animal Shelter"}
                        """));

        PartnerRegistryResult result = client.lookup("CHIP-123");

        assertThat(result).isEqualTo(new PartnerRegistryResult(true, "Riga Animal Shelter"));

        RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).isEqualTo("/registry/CHIP-123");
    }

    @Test
    void lookup_shouldReturnNotRegisteredWhenPartnerHasNoRecord() {
        server.enqueue(new MockResponse()
                .setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""
                        {"registered": false, "partnerShelterName": null}
                        """));

        PartnerRegistryResult result = client.lookup("CHIP-404");

        assertThat(result.registered()).isFalse();
        assertThat(result.partnerShelterName()).isNull();
    }

    @Test
    void lookup_shouldThrowServerErrorWhenPartnerFails() {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));

        assertThatThrownBy(() -> client.lookup("CHIP-123"))
                .isInstanceOf(HttpServerErrorException.InternalServerError.class);

        assertThat(server.getRequestCount()).isEqualTo(1);
    }

    @Test
    void lookup_shouldThrowClientErrorWhenPartnerReturns404() {
        server.enqueue(new MockResponse().setResponseCode(404));

        assertThatThrownBy(() -> client.lookup("CHIP-123"))
                .isInstanceOf(HttpClientErrorException.NotFound.class);
    }
}
