package uk.gov.justice.laa.rcw.service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.rcw.model.Application;

/** Service class for handling Application requests. */
@Slf4j
@Service
public class ApplicationService {

  /**
   * Gets all Applications.
   *
   * @return the list of Applications
   */
  public List<Application> getApplications() {
    log.info("Retrieving all applications");
    // TODO: replace with downstream API call
    return List.of(
        Application.builder()
            .id(UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890"))
            .name("Random Name")
            .dob(LocalDate.of(1985, 3, 14))
            .address("Random Address")
            .build(),
        Application.builder()
            .id(UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901"))
            .name("Other Random Name")
            .dob(LocalDate.of(1972, 11, 28))
            .address("Other Random Address")
            .build());
  }
}
