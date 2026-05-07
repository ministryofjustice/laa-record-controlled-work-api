package uk.gov.justice.laa.rcw.actuator;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@TestPropertySource(
    properties = {
      "management.endpoints.web.exposure.include=health",
    })
class ActuatorTest {

  @LocalManagementPort private int port;

  @Autowired private TestRestTemplate restTemplate;

  @Test
  void actuatorHealthEndpointShouldReturnUp() {
    ResponseEntity<String> result =
        restTemplate.getForEntity("http://localhost:" + port + "/actuator/health", String.class);

    assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(result.getBody()).contains("\"status\":\"UP\"");
  }
}
