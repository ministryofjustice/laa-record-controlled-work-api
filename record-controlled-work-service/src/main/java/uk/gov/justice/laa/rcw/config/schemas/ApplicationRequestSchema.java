package uk.gov.justice.laa.rcw.config.schemas;

/** OpenAPI schemas used to validate application requests. */
public enum ApplicationRequestSchema {
  CREATE_APPLICATION("CreateApplicationRequestBody"),
  UPDATE_APPLICATION_DETAILS("UpdateApplicationDetailsRequestBody");

  private final String componentName;

  ApplicationRequestSchema(String componentName) {
    this.componentName = componentName;
  }

  public String componentName() {
    return componentName;
  }
}
