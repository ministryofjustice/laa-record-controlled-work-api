package uk.gov.justice.laa.rcw.service;

import static uk.gov.justice.laa.rcw.logging.LogAction.APPLICATION_CREATE;
import static uk.gov.justice.laa.rcw.logging.LogAction.APPLICATION_FETCH;
import static uk.gov.justice.laa.rcw.logging.LogAction.APPLICATION_LIST;
import static uk.gov.justice.laa.rcw.logging.LogAction.APPLICATION_MEANS_UPDATE;

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
import org.springframework.web.client.HttpClientErrorException;
import uk.gov.justice.laa.ia.datastore.client.api.ApplicationApi;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponses;
import uk.gov.justice.laa.ia.datastore.client.model.UpdateMeansDataCommand;
import uk.gov.justice.laa.rcw.exception.ApplicationConflictException;
import uk.gov.justice.laa.rcw.exception.ApplicationNotFoundException;
import uk.gov.justice.laa.rcw.logging.StructuredLogger;
import uk.gov.justice.laa.rcw.mapper.ApplicationMapper;
import uk.gov.justice.laa.rcw.model.Address;
import uk.gov.justice.laa.rcw.model.Application;
import uk.gov.justice.laa.rcw.model.ApplicationOverview;
import uk.gov.justice.laa.rcw.model.ApplicationState;
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
      Integer page, Integer size, String officeId, ApplicationState status) {
    ApplicationResponses responses =
        applicationApi.getApplications(
            currentBearerToken(), page, size, officeId, toDatastoreApplicationState(status));
    List<ApplicationOverview> applications =
        responses.getContent().stream().map(applicationMapper::toApplicationOverview).toList();
    log.info()
        .action(APPLICATION_LIST)
        .outcome("success")
        .log("Retrieved {} applications", applications.size());
    return applications;
  }

  private uk.gov.justice.laa.ia.datastore.client.model.ApplicationState toDatastoreApplicationState(
      ApplicationState status) {
    if (status == null) {
      return null;
    }
    return switch (status) {
      case DRAFT -> uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT;
      case COMPLETED -> uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.COMPLETED;
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
                .applicationState(ApplicationState.DRAFT)
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
   * Updates the means data for an application. The datastore requires an eTag for optimistic
   * concurrency, so the current application is fetched first to source it; if the update conflicts
   * with a concurrent modification, the eTag is re-fetched and the update is retried once.
   *
   * @param applicationId the application id
   * @param data the means Q&amp;A data
   * @param result the means calculation result
   */
  public void updateMeans(UUID applicationId, Object data, Object result) {
    updateMeans(applicationId, data, result, true);
  }

  private void updateMeans(
      UUID applicationId, Object data, Object result, boolean retryOnConflict) {
    Long etag = fetchApplication(applicationId).geteTag();
    UpdateMeansDataCommand command =
        UpdateMeansDataCommand.builder().eTag(etag).data(data).result(result).build();
    try {
      applicationApi.updateMeansData(applicationId, currentBearerToken(), command);
    } catch (HttpClientErrorException.NotFound exception) {
      throw applicationNotFound(applicationId);
    } catch (HttpClientErrorException.Conflict exception) {
      if (!retryOnConflict) {
        throw new ApplicationConflictException(
            "Application %s was modified concurrently".formatted(applicationId));
      }
      updateMeans(applicationId, data, result, false);
      return;
    }
    log.info()
        .action(APPLICATION_MEANS_UPDATE)
        .outcome("success")
        .with("application.id", applicationId)
        .log("Updated means data for application {}", applicationId);
  }

  private ApplicationResponse fetchApplication(UUID applicationId) {
    try {
      return applicationApi.getApplication(applicationId, currentBearerToken());
    } catch (HttpClientErrorException.NotFound exception) {
      throw applicationNotFound(applicationId);
    }
  }

  private ApplicationNotFoundException applicationNotFound(UUID applicationId) {
    return new ApplicationNotFoundException(
        "No application found with id: %s".formatted(applicationId));
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
