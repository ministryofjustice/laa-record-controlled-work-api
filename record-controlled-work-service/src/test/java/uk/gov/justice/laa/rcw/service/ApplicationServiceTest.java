package uk.gov.justice.laa.rcw.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.rcw.generator.ApplicationGenerator;
import uk.gov.justice.laa.rcw.generator.ApplicationOverviewGenerator;
import uk.gov.justice.laa.rcw.model.Application;
import uk.gov.justice.laa.rcw.model.ApplicationOverview;
import uk.gov.justice.laa.rcw.model.ApplicationStatus;

class ApplicationServiceTest {

  ApplicationService applicationService = new ApplicationService();

  @Test
  void shouldGetAllApplications() {
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
            1, 1, UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901"), ApplicationStatus.DRAFT);

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
