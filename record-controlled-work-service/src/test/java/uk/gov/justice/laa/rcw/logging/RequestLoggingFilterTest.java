package uk.gov.justice.laa.rcw.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLoggingFilterTest {

  private final RequestLoggingFilter filter = new RequestLoggingFilter();

  private Logger logger;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void attachAppender() {
    logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    logger.detachAppender(appender);
  }

  @Test
  void logsRequestWithRequiredStructuredFields() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/applications");
    request.setRemoteAddr("10.0.0.1");
    MockHttpServletResponse response = new MockHttpServletResponse();
    response.setStatus(200);

    filter.doFilterInternal(request, response, (req, res) -> {});

    assertThat(appender.list).hasSize(1);
    ILoggingEvent event = appender.list.get(0);

    assertThat(keyValue(event, "event.action")).isEqualTo(LogAction.REQUEST_RECEIVED);
    assertThat(keyValue(event, "event.outcome")).isEqualTo("success");
    assertThat(keyValue(event, "http.request.method")).isEqualTo("GET");
    assertThat(keyValue(event, "url.path")).isEqualTo("/api/v1/applications");
    assertThat(keyValue(event, "http.response.status_code")).isEqualTo(200);
    assertThat(keyValue(event, "client.ip")).isEqualTo("10.0.0.1");
    assertThat((Long) keyValue(event, "event.duration_ms")).isGreaterThanOrEqualTo(0);
  }

  @Test
  void usesFirstAddressFromXforwardedForHeader() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/applications");
    request.addHeader("X-Forwarded-For", "203.0.113.5, 70.41.3.18, 10.0.0.1");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, (req, res) -> {});

    assertThat(keyValue(appender.list.getFirst(), "client.ip")).isEqualTo("203.0.113.5");
  }

  @Test
  void fallsBackToRemoteAddressWhenNoXforwardedFor() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/applications");
    request.setRemoteAddr("192.168.1.1");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, (req, res) -> {});

    assertThat(keyValue(appender.list.getFirst(), "client.ip")).isEqualTo("192.168.1.1");
  }

  @Test
  void messageIsRequestHandled() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/applications");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, (req, res) -> {});

    assertThat(appender.list.getFirst().getFormattedMessage()).isEqualTo("Request handled");
  }

  @Test
  void suppressesActuatorRequests() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");

    assertThat(filter.shouldNotFilter(request)).isTrue();
  }

  private Object keyValue(ILoggingEvent event, String key) {
    return event.getKeyValuePairs().stream()
        .filter(kv -> kv.key.equals(key))
        .map(kv -> kv.value)
        .findFirst()
        .orElse(null);
  }
}
