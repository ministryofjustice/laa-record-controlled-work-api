package uk.gov.justice.laa.rcw.service;

import static uk.gov.justice.laa.rcw.logging.LogAction.APPLICATION_CREATE;

import java.util.UUID;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.rcw.logging.StructuredLogger;
import uk.gov.justice.laa.rcw.model.CreateApplicationRequestBody;
import uk.gov.justice.laa.rcw.model.CreateApplicationResponseBody;

/** Service class for creating Applications. */
@Service
public class ApplicationCreationService {

  private static final StructuredLogger log = StructuredLogger.of(ApplicationCreationService.class);

  /**
   * Create application. This is a temporary return so that we can test the integration before
   * connecting to the data store. TODO: Replace with Data Store API call
   *
   * @return the request body with the created ID
   */
  public CreateApplicationResponseBody createApplication(
      CreateApplicationRequestBody applicationRequestBody) {

    CreateApplicationResponseBody responseBody = new CreateApplicationResponseBody();

    BeanUtils.copyProperties(applicationRequestBody, responseBody);

    responseBody.id(UUID.fromString("69e24085-60f9-43c5-9574-7544502f6905"));

    log.info()
        .action(APPLICATION_CREATE)
        .outcome("success")
        .with("application.id", responseBody.getId())
        .log("Created application {}", responseBody.getId());
    return responseBody;
  }
}
