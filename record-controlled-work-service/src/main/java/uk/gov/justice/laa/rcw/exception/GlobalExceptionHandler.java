package uk.gov.justice.laa.rcw.exception;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static uk.gov.justice.laa.rcw.logging.LogAction.APPLICATION_DOWNSTREAM_ERROR;
import static uk.gov.justice.laa.rcw.logging.LogAction.APPLICATION_ERROR;
import static uk.gov.justice.laa.rcw.logging.LogAction.REQUEST_INVALID;
import static uk.gov.justice.laa.rcw.logging.LogAction.REQUEST_VALIDATION_FAILED;

import java.net.URI;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import uk.gov.justice.laa.rcw.logging.StructuredLogger;

/** The global exception handler for all exceptions. */
@Profile("!local") // disable local profiles to allow exceptions to propagate for development
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final StructuredLogger log = StructuredLogger.of(GlobalExceptionHandler.class);
  private static final URI DEFAULT_PROBLEM_TYPE = URI.create("about:blank");

  /**
   * The handler for ItemNotFoundException.
   *
   * @param exception the exception
   * @return the response status with error message
   */
  @ExceptionHandler(ItemNotFoundException.class)
  public ResponseEntity<Object> handleItemNotFound(
      ItemNotFoundException exception, WebRequest request) {
    return handleKnownException(exception, NOT_FOUND, request);
  }

  /**
   * The handler for ApplicationNotFoundException.
   *
   * @param exception the exception
   * @return the response status with error message
   */
  @ExceptionHandler(ApplicationNotFoundException.class)
  public ResponseEntity<Object> handleApplicationNotFound(
      ApplicationNotFoundException exception, WebRequest request) {
    return handleKnownException(exception, NOT_FOUND, request);
  }

  /**
   * The handler for ApplicationConflictException.
   *
   * @param exception the exception
   * @return the response status with error message
   */
  @ExceptionHandler(ApplicationConflictException.class)
  public ResponseEntity<Object> handleApplicationConflict(
      ApplicationConflictException exception, WebRequest request) {
    return handleKnownException(exception, CONFLICT, request);
  }

  /**
   * The handler for ApplicationForbiddenException.
   *
   * @param exception the exception
   * @return the response status with error message
   */
  @ExceptionHandler(ApplicationForbiddenException.class)
  public ResponseEntity<Object> handleApplicationForbidden(
      ApplicationForbiddenException exception, WebRequest request) {
    return handleKnownException(exception, FORBIDDEN, request);
  }

  /**
   * The handler for ApplicationBadRequestException.
   *
   * @param exception the exception
   * @return the response status with error message
   */
  @ExceptionHandler(ApplicationBadRequestException.class)
  public ResponseEntity<Object> handleApplicationBadRequest(
      ApplicationBadRequestException exception, WebRequest request) {
    log.error(exception)
        .action(APPLICATION_ERROR)
        .outcome("failure")
        .with("http.response.status_code", BAD_REQUEST.value())
        .log("Datastore rejected the request as invalid");
    return handleKnownException(exception, BAD_REQUEST, request);
  }

  /**
   * The handler for ApplicationUpstreamErrorException.
   *
   * @param exception the exception
   * @return the response status with error message
   */
  @ExceptionHandler(ApplicationUpstreamErrorException.class)
  public ResponseEntity<Object> handleApplicationUpstreamError(
      ApplicationUpstreamErrorException exception, WebRequest request) {
    log.warn()
        .action(APPLICATION_DOWNSTREAM_ERROR)
        .outcome("failure")
        .with("http.response.status_code", BAD_GATEWAY.value())
        .log("Datastore returned an error");
    return handleKnownException(exception, BAD_GATEWAY, request);
  }

  /**
   * The handler for ApplicationUnavailableException.
   *
   * @param exception the exception
   * @return the response status with error message
   */
  @ExceptionHandler(ApplicationUnavailableException.class)
  public ResponseEntity<Object> handleApplicationUnavailable(
      ApplicationUnavailableException exception, WebRequest request) {
    log.warn()
        .action(APPLICATION_DOWNSTREAM_ERROR)
        .outcome("failure")
        .with("http.response.status_code", SERVICE_UNAVAILABLE.value())
        .log("Datastore is unavailable");
    return handleKnownException(exception, SERVICE_UNAVAILABLE, request);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      @NonNull HttpMessageNotReadableException exception,
      @NonNull HttpHeaders headers,
      @NonNull HttpStatusCode status,
      @NonNull WebRequest request) {
    log.warn()
        .action(REQUEST_INVALID)
        .outcome("failure")
        .with("http.response.status_code", BAD_REQUEST.value())
        .with("url.path", getRequestPath(request))
        .log("Invalid request content");
    return handleInvalidRequestContent(exception, headers, request);
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception,
      @NonNull HttpHeaders headers,
      @NonNull HttpStatusCode status,
      @NonNull WebRequest request) {
    log.warn()
        .action(REQUEST_VALIDATION_FAILED)
        .outcome("failure")
        .with("http.response.status_code", BAD_REQUEST.value())
        .with("url.path", getRequestPath(request))
        .log("Validation failed: {} error(s)", exception.getBindingResult().getErrorCount());
    return handleInvalidRequestContent(exception, headers, request);
  }

  /**
   * The handler for Exception.
   *
   * @param exception the exception
   * @return the response status with error message
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<String> handleGenericException(Exception exception) {
    log.error(exception)
        .action(APPLICATION_ERROR)
        .outcome("failure")
        .with("http.response.status_code", INTERNAL_SERVER_ERROR.value())
        .log("An unexpected application error has occurred");
    return ResponseEntity.internalServerError()
        .body("An unexpected application error has occurred.");
  }

  private ResponseEntity<Object> handleInvalidRequestContent(
      Exception exception, HttpHeaders headers, WebRequest request) {
    ProblemDetail problemDetail =
        buildProblemDetail(BAD_REQUEST, "Invalid request content.", request);
    return handleExceptionInternal(exception, problemDetail, headers, BAD_REQUEST, request);
  }

  private ResponseEntity<Object> handleKnownException(
      RuntimeException exception, HttpStatusCode status, WebRequest request) {
    ProblemDetail problemDetail = buildProblemDetail(status, exception.getMessage(), request);
    return handleExceptionInternal(exception, problemDetail, new HttpHeaders(), status, request);
  }

  private ProblemDetail buildProblemDetail(
      HttpStatusCode status, String detail, WebRequest request) {
    ProblemDetail problemDetail = ProblemDetail.forStatus(status);
    problemDetail.setType(DEFAULT_PROBLEM_TYPE);
    problemDetail.setDetail(detail);
    problemDetail.setInstance(getRequestUri(request));
    return problemDetail;
  }

  private URI getRequestUri(WebRequest request) {
    if (request instanceof ServletWebRequest servletWebRequest) {
      return URI.create(servletWebRequest.getRequest().getRequestURI());
    }
    return URI.create("");
  }

  private String getRequestPath(WebRequest request) {
    if (request instanceof ServletWebRequest servletWebRequest) {
      return servletWebRequest.getRequest().getRequestURI();
    }
    return "";
  }
}
