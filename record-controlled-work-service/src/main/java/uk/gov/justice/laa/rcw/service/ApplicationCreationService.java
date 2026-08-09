package uk.gov.justice.laa.rcw.service;

import static uk.gov.justice.laa.rcw.logging.LogAction.APPLICATION_CREATE;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.ia.datastore.client.api.ApplicationApi;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.UpdateScopingDataCommand;
import uk.gov.justice.laa.rcw.logging.StructuredLogger;
import uk.gov.justice.laa.rcw.mapper.ApplicationMapper;
import uk.gov.justice.laa.rcw.model.Application;
import uk.gov.justice.laa.rcw.model.CreateApplicationRequestBody;

/** Service class for creating Applications. */
@Service
@RequiredArgsConstructor
public class ApplicationCreationService {

  private static final StructuredLogger log = StructuredLogger.of(ApplicationCreationService.class);

  private final ApplicationApi applicationApi;
  private final ApplicationMapper applicationMapper;
  private final BearerTokenProvider bearerTokenProvider;

  /**
   * Creates an application by forwarding the RCW request to the datastore.
   *
   * @return the created application
   */
  public Application createApplication(CreateApplicationRequestBody applicationRequestBody) {
    String bearerToken = bearerTokenProvider.currentBearerToken();
    ApplicationResponse applicationResponse =
        applicationApi.startApplication(
            bearerToken, applicationMapper.toStartApplicationCommand(applicationRequestBody));

    applicationApi.updateScopingData(
        applicationResponse.getId(),
        bearerToken,
        UpdateScopingDataCommand.builder()
            .eTag(applicationResponse.geteTag())
            .scopingQuestions(applicationRequestBody.getScopingQuestions())
            .build());

    Application application = applicationMapper.toApplication(applicationResponse);

    log.info()
        .action(APPLICATION_CREATE)
        .outcome("success")
        .with("application.id", application.getId())
        .log("Created application {}", application.getId());
    return application;
  }
}
