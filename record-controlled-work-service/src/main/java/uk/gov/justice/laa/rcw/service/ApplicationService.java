package uk.gov.justice.laa.rcw.service;

import static uk.gov.justice.laa.rcw.logging.LogAction.APPLICATION_CREATE;
import static uk.gov.justice.laa.rcw.logging.LogAction.APPLICATION_FETCH;
import static uk.gov.justice.laa.rcw.logging.LogAction.APPLICATION_LIST;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.ia.datastore.client.api.ApplicationApi;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponses;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationState;
import uk.gov.justice.laa.rcw.logging.StructuredLogger;
import uk.gov.justice.laa.rcw.mapper.ApplicationMapper;
import uk.gov.justice.laa.rcw.model.Address;
import uk.gov.justice.laa.rcw.model.Application;
import uk.gov.justice.laa.rcw.model.ApplicationOverview;
import uk.gov.justice.laa.rcw.model.ApplicationStatus;
import uk.gov.justice.laa.rcw.model.ClientDeclarationStatus;
import uk.gov.justice.laa.rcw.model.ClientDetails;
import uk.gov.justice.laa.rcw.model.CreateApplicationRequestBody;
import uk.gov.justice.laa.rcw.model.CreateApplicationResponseBody;
import uk.gov.justice.laa.rcw.model.Declaration;
import uk.gov.justice.laa.rcw.model.Evidence;
import uk.gov.justice.laa.rcw.model.EvidenceStatus;

/** Service class for handling Application requests. */
@Service
@RequiredArgsConstructor
public class ApplicationService {

  private static final StructuredLogger log = StructuredLogger.of(ApplicationService.class);

  private final ApplicationApi applicationApi;
  private final ApplicationMapper applicationMapper;

  /**
   * Gets all Applications.
   *
   * @return the list of Applications
   */
  public List<ApplicationOverview> getApplications(
      Integer page, Integer size, String officeId, ApplicationStatus status) {
    ApplicationResponses responses =
        applicationApi.getApplications(
            currentBearerToken(), page, size, officeId, toApplicationState(status));
    List<ApplicationOverview> applications =
        responses.getContent().stream().map(applicationMapper::toApplicationOverview).toList();
    log.info()
        .action(APPLICATION_LIST)
        .outcome("success")
        .log("Retrieved {} applications", applications.size());
    return applications;
  }

  private ApplicationState toApplicationState(ApplicationStatus status) {
    if (status == null) {
      return null;
    }
    return switch (status) {
      case DRAFT -> ApplicationState.DRAFT;
      case COMPLETE -> ApplicationState.COMPLETED;
    };
  }

  /** Forwards the original incoming middleware token unchanged, as required by the datastore. */
  private String currentBearerToken() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication instanceof AbstractOAuth2TokenAuthenticationToken<?> tokenAuth) {
      return "Bearer " + tokenAuth.getToken().getTokenValue();
    }
    throw new IllegalStateException("No authenticated token available to forward to the datastore");
  }

  /**
   * Gets an Application or empty optional if not found.
   *
   * @return {@link Optional} of {@link Application}
   */
  public Optional<Application> getApplication(UUID applicationId) {
    // TODO: replace with downstream API call

    Address address =
        Address.builder()
            .id(UUID.randomUUID())
            .addressLine1("10 Downing Street")
            .addressLine2("Prime ministers address")
            .postCode("SW1A 2AA")
            .townOrCity("London")
            .country("GB")
            .build();

    ClientDetails clientDetails =
        ClientDetails.builder()
            .id(UUID.randomUUID())
            .firstName("Joe")
            .lastName("Bloggs")
            .niNumber("QQ123456C")
            .dateOfBirth(LocalDate.of(1990, 1, 1))
            .hasFixedAddress(true)
            .address(address)
            .build();

    Declaration declaration =
        Declaration.builder()
            .id(UUID.randomUUID())
            .clientDeclarationStatus(ClientDeclarationStatus.DRAFT)
            .declarationConfirmation(false)
            .createdAt(OffsetDateTime.now())
            .modifiedAt(OffsetDateTime.now())
            .createdBy("Joe Bloggs")
            .modifiedBy("James Bloggs")
            .build();

    Evidence evidence =
        Evidence.builder()
            .id(UUID.randomUUID())
            .createdAt(OffsetDateTime.now())
            .modifiedAt(OffsetDateTime.now())
            .evidenceStatus(EvidenceStatus.DRAFT)
            .payeIncomeEvidence(false)
            .otherIncomeEvidence(false)
            .housingCostsEvidence(false)
            .capitalEvidence(false)
            .createdBy("Joe Bloggs")
            .modifiedBy("James Bloggs")
            .build();

    Optional<Application> application =
        Optional.of(
            Application.builder()
                .id(applicationId)
                .individualLegalAidNumber(UUID.fromString("ebd50ba0-9ed9-4003-83a8-c11ac07d9e32"))
                .providerFirmCode("123456")
                .providerOfficeCode("22439e72-68d3-4770-b435-c352d883d21e")
                .createdAt(OffsetDateTime.now())
                .createdBy("Random User")
                .clientDetails(clientDetails)
                .applicationStatus(ApplicationStatus.DRAFT)
                .declaration(declaration)
                .evidence(evidence)
                .modifiedAt(OffsetDateTime.now())
                .modifiedBy("Random User")
                .build());
    log.info()
        .action(APPLICATION_FETCH)
        .outcome("success")
        .with("application.id", applicationId)
        .log("Retrieved application {}", applicationId);
    return application;
  }

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
