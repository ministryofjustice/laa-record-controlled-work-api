package uk.gov.justice.laa.rcw.service;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.justice.laa.rcw.model.ApplicationStatus.COMPLETE;
import static uk.gov.justice.laa.rcw.model.ApplicationStatus.DRAFT;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.rcw.generator.ApplicationGenerator;
import uk.gov.justice.laa.rcw.generator.ApplicationOverviewGenerator;
import uk.gov.justice.laa.rcw.model.Application;
import uk.gov.justice.laa.rcw.model.ApplicationOverview;

class ApplicationServiceTest {

  ApplicationService applicationService = new ApplicationService();

  @Test
  void shouldGetAllApplications_WithDraftStatus() {
    List<ApplicationOverview> applications =
        List.of(
            ApplicationOverviewGenerator.create(null),
            ApplicationOverviewGenerator.create(
                b ->
                    b.id(UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901"))
                        .name("Other Random Name")
                        .modifiedAt(OffsetDateTime.now())
                        .applicationRefNumber("CW-222222")));

    List<ApplicationOverview> result =
        applicationService.getApplications(
            1, 1, UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901"), DRAFT);

    assertThat(result)
        .usingRecursiveComparison()
        .ignoringFields("modifiedAt")
        .isEqualTo(applications);
  }

  @Test
  void shouldGetAllApplications_WithCompleteStatus() {
    List<ApplicationOverview> applications =
        List.of(
            ApplicationOverviewGenerator.create(
                b ->
                    b.id(UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890"))
                        .name("Hey im Recorded")
                        .modifiedAt(OffsetDateTime.now())
                        .applicationRefNumber("CW-111111")));

    List<ApplicationOverview> result =
        applicationService.getApplications(
            1, 1, UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890"), COMPLETE);

    assertThat(result)
        .usingRecursiveComparison()
        .ignoringFields("modifiedAt")
        .isEqualTo(applications);
  }

  @Test
  void shouldGetApplicationById() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    Optional<Application> expected =
        Optional.of(ApplicationGenerator.create(b -> b.id(applicationId)));

    Optional<Application> result = applicationService.getApplication(applicationId);

    assertThat(result).isPresent();

    assertThat(result)
        .usingRecursiveComparison()
        .ignoringFields(
            "value.individualLegalAidNumber",
            "value.providerFirmId",
            "value.providerOfficeId",
            "value.createdAt",
            "value.modifiedAt",
            "value.clientDetails.id",
            "value.clientDetails.createdAt",
            "value.clientDetails.modifiedAt",
            "value.clientDetails.address.id",
            "value.clientDetails.address.createdAt",
            "value.clientDetails.address.modifiedAt",
            "value.declaration.id",
            "value.declaration.createdAt",
            "value.declaration.modifiedAt",
            "value.evidence.id",
            "value.evidence.createdAt",
            "value.evidence.modifiedAt")
        .isEqualTo(expected);
  }
}
