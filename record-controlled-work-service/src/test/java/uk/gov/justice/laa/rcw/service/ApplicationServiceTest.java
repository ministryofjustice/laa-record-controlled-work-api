package uk.gov.justice.laa.rcw.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.rcw.generator.ApplicationOverviewGenerator;
import uk.gov.justice.laa.rcw.model.ApplicationOverview;

class ApplicationServiceTest {

  ApplicationService applicationService = new ApplicationService();

  @Test
  void shouldGetAllItems() {
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
}
