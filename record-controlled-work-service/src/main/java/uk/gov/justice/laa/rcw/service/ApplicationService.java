package uk.gov.justice.laa.rcw.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
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
@Slf4j
@Service
public class ApplicationService {

  /**
   * Gets all Applications.
   *
   * @return the list of Applications
   */
  public List<ApplicationOverview> getApplications(
      Integer page, Integer size, UUID officeId, ApplicationStatus status) {
    log.info("Retrieving all applications");
    // TODO: replace with downstream API call
    if (status == ApplicationStatus.DRAFT) {
      return List.of(
          ApplicationOverview.builder()
              .id(UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890"))
              .name("Random Name")
              .modifiedAt(OffsetDateTime.now())
              .applicationRefNumber("CW-111111")
              .build(),
          ApplicationOverview.builder()
              .id(UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901"))
              .name("Other Random Name")
              .modifiedAt(OffsetDateTime.now())
              .applicationRefNumber("CW-222222")
              .build());
    }
    return List.of(
        ApplicationOverview.builder()
            .id(UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890"))
            .name("Hey im Recorded")
            .modifiedAt(OffsetDateTime.now())
            .applicationRefNumber("CW-111111")
            .build());
  }

  /**
   * Gets an Application or empty optional if not found.
   *
   * @return {@link Optional} of {@link Application}
   */
  public Optional<Application> getApplication(UUID applicationId) {
    log.info("Retrieving application");
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
            .niNumber("AB123456Q")
            .dateOfBirth(LocalDate.of(1990, 1, 1))
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

    return Optional.of(
        Application.builder()
            .id(applicationId)
            .individualLegalAidNumber(UUID.fromString("ebd50ba0-9ed9-4003-83a8-c11ac07d9e32"))
            .providerFirmId(UUID.randomUUID())
            .providerOfficeId(UUID.fromString("22439e72-68d3-4770-b435-c352d883d21e"))
            .createdAt(OffsetDateTime.now())
            .createdBy("Random User")
            .clientDetails(clientDetails)
            .applicationStatus(ApplicationStatus.DRAFT)
            .declaration(declaration)
            .evidence(evidence)
            .modifiedAt(OffsetDateTime.now())
            .modifiedBy("Random User")
            .build());
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

    return responseBody;
  }
}
