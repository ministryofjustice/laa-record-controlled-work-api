package uk.gov.justice.laa.rcw.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import uk.gov.justice.laa.rcw.model.Address;
import uk.gov.justice.laa.rcw.model.Application;
import uk.gov.justice.laa.rcw.model.ApplicationState;
import uk.gov.justice.laa.rcw.model.ClientDeclarationStatus;
import uk.gov.justice.laa.rcw.model.ClientDetails;
import uk.gov.justice.laa.rcw.model.Declaration;
import uk.gov.justice.laa.rcw.model.Evidence;
import uk.gov.justice.laa.rcw.model.EvidenceStatus;

final class StubApplicationFactory {

  private StubApplicationFactory() {}

  static Application stubApplication(UUID applicationId) {
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

    return Application.builder()
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
        .ecfFlag(false)
        .applicationType("UNKNOWN")
        .modifiedAt(OffsetDateTime.now())
        .modifiedBy("Random User")
        .build();
  }
}
