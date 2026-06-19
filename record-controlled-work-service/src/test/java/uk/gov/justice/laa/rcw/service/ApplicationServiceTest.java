package uk.gov.justice.laa.rcw.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.rcw.generator.ApplicationOverviewGenerator;
import uk.gov.justice.laa.rcw.generator.ApplicationResponseGenerator;
import uk.gov.justice.laa.rcw.model.ApplicationOverview;
import uk.gov.justice.laa.rcw.model.ApplicationResponse;

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

    List<ApplicationOverview> result = applicationService.getApplications();

    assertThat(result)
        .usingRecursiveComparison()
        .ignoringFields("modifiedAt")
        .isEqualTo(applications);
  }

  @Test
  void shouldGetApplicationById() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    Optional<ApplicationResponse> expected =
        Optional.of(ApplicationResponseGenerator.create(b -> b.id(applicationId)));

    Optional<ApplicationResponse> result = applicationService.getApplication(applicationId);

    assertThat(result).isPresent();
    assertThat(result)
        .usingRecursiveComparison()
        .ignoringFields("value.modifiedAt", "value.createdAt")
        .isEqualTo(expected);
  }
}
