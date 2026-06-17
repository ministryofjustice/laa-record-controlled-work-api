package uk.gov.justice.laa.rcw.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.rcw.generator.ApplicationGenerator;
import uk.gov.justice.laa.rcw.model.Application;

class ApplicationServiceTest {

  ApplicationService applicationService = new ApplicationService();

  @Test
  void shouldGetAllItems() {
    List<Application> applications =
        List.of(
            ApplicationGenerator.create(null),
            ApplicationGenerator.create(
                b ->
                    b.id(UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901"))
                        .name("Other Random Name")
                        .dob(LocalDate.of(1972, 11, 28))
                        .address("Other Random Address")));

    List<Application> result = applicationService.getApplications();

    assertThat(result).hasSize(2).isEqualTo(applications);
  }
}
