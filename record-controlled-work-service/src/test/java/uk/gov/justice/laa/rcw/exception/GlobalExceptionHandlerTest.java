package uk.gov.justice.laa.rcw.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

class GlobalExceptionHandlerTest {

  GlobalExceptionHandler globalExceptionHandler = new GlobalExceptionHandler();

  @Test
  void handleGenericException_returnsInternalServerErrorStatusAndErrorMessage() {
    ResponseEntity<String> result =
        globalExceptionHandler.handleGenericException(new RuntimeException("Something went wrong"));

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(INTERNAL_SERVER_ERROR);
    assertThat(result.getBody()).isNotNull();
    assertThat(result.getBody()).isEqualTo("An unexpected application error has occurred.");
  }

  @Test
  void handleApplicationForbidden_returnsForbiddenStatusAndErrorMessage() {
    MockHttpServletRequest request =
        new MockHttpServletRequest("PUT", "/api/v1/applications/99/means");
    ResponseEntity<Object> result =
        globalExceptionHandler.handleApplicationForbidden(
            new ApplicationForbiddenException("Not authorized to update application 99"),
            new ServletWebRequest(request));

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(FORBIDDEN);
    ProblemDetail body = (ProblemDetail) result.getBody();
    assert body != null;
    assertThat(body.getDetail()).isEqualTo("Not authorized to update application 99");
  }

  @Test
  void handleApplicationBadRequest_returnsBadRequestStatusAndErrorMessage() {
    MockHttpServletRequest request =
        new MockHttpServletRequest("PUT", "/api/v1/applications/99/means");
    ResponseEntity<Object> result =
        globalExceptionHandler.handleApplicationBadRequest(
            new ApplicationBadRequestException("Datastore rejected the request for application 99"),
            new ServletWebRequest(request));

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(BAD_REQUEST);
    ProblemDetail body = (ProblemDetail) result.getBody();
    assert body != null;
    assertThat(body.getDetail()).isEqualTo("Datastore rejected the request for application 99");
  }

  @Test
  void handleApplicationUpstreamError_returnsBadGatewayStatusAndErrorMessage() {
    MockHttpServletRequest request =
        new MockHttpServletRequest("PUT", "/api/v1/applications/99/means");
    ResponseEntity<Object> result =
        globalExceptionHandler.handleApplicationUpstreamError(
            new ApplicationUpstreamErrorException("Datastore returned an error for application 99"),
            new ServletWebRequest(request));

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(BAD_GATEWAY);
    ProblemDetail body = (ProblemDetail) result.getBody();
    assert body != null;
    assertThat(body.getDetail()).isEqualTo("Datastore returned an error for application 99");
  }

  @Test
  void handleApplicationUnavailable_returnsServiceUnavailableStatusAndErrorMessage() {
    MockHttpServletRequest request =
        new MockHttpServletRequest("PUT", "/api/v1/applications/99/means");
    ResponseEntity<Object> result =
        globalExceptionHandler.handleApplicationUnavailable(
            new ApplicationUnavailableException("Datastore is unavailable for application 99"),
            new ServletWebRequest(request));

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(SERVICE_UNAVAILABLE);
    ProblemDetail body = (ProblemDetail) result.getBody();
    assert body != null;
    assertThat(body.getDetail()).isEqualTo("Datastore is unavailable for application 99");
  }
}
