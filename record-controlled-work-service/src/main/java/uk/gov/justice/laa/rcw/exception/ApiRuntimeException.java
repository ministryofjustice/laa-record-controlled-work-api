package uk.gov.justice.laa.rcw.exception;

import lombok.Getter;

/** Base type for RCW application exceptions carrying a machine-readable violation reason. */
@Getter
public abstract class ApiRuntimeException extends RuntimeException {

  private final String reason;

  protected ApiRuntimeException(String message, String reason) {
    super(message);
    this.reason = reason;
  }
}
