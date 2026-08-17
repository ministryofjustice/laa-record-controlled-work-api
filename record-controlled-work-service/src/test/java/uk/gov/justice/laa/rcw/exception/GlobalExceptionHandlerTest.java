package uk.gov.justice.laa.rcw.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
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
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/applications");
    ResponseEntity<Object> result =
        globalExceptionHandler.handleGenericException(
            new RuntimeException("Something went wrong"), new ServletWebRequest(request));

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(INTERNAL_SERVER_ERROR);
    ProblemDetail body = (ProblemDetail) result.getBody();
    assert body != null;
    assertThat(body.getDetail()).isEqualTo("An unexpected application error has occurred.");
    assertThat(body.getProperties()).containsEntry("reason", "INTERNAL_SERVER_ERROR");
  }

  @Test
  void handleApplicationConflict_returnsConflictStatusAndDefaultReason_whenModifiedConcurrently() {
    MockHttpServletRequest request =
        new MockHttpServletRequest("PUT", "/api/v1/applications/99/means");
    ResponseEntity<Object> result =
        globalExceptionHandler.handleApplicationConflict(
            new ApplicationConflictException("Application 99 was modified concurrently"),
            new ServletWebRequest(request));

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(CONFLICT);
    ProblemDetail body = (ProblemDetail) result.getBody();
    assert body != null;
    assertThat(body.getProperties()).containsEntry("reason", "CONCURRENT_MODIFICATION");
  }

  @Test
  void handleApplicationConflict_returnsConflictStatusAndGivenReason_whenAlreadyRecorded() {
    MockHttpServletRequest request =
        new MockHttpServletRequest("PUT", "/api/v1/applications/99/means");
    ResponseEntity<Object> result =
        globalExceptionHandler.handleApplicationConflict(
            new ApplicationConflictException(
                "Application 99 has already been recorded and cannot be updated",
                "APPLICATION_ALREADY_RECORDED"),
            new ServletWebRequest(request));

    assertThat(result).isNotNull();
    assertThat(result.getStatusCode()).isEqualTo(CONFLICT);
    ProblemDetail body = (ProblemDetail) result.getBody();
    assert body != null;
    assertThat(body.getProperties()).containsEntry("reason", "APPLICATION_ALREADY_RECORDED");
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
    assertThat(body.getProperties()).containsEntry("reason", "OFFICE_NOT_AUTHORIZED");
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
    assertThat(body.getProperties()).containsEntry("reason", "DATASTORE_REJECTED_REQUEST");
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
    assertThat(body.getProperties()).containsEntry("reason", "DATASTORE_SERVER_ERROR");
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
    assertThat(body.getProperties()).containsEntry("reason", "DATASTORE_UNAVAILABLE");
  }
}
