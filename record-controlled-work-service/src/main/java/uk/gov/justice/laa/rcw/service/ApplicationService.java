package uk.gov.justice.laa.rcw.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.rcw.model.ApplicationOverview;
import uk.gov.justice.laa.rcw.model.ApplicationResponse;

/** Service class for handling Application requests. */
@Slf4j
@Service
public class ApplicationService {

  /**
   * Gets all Applications.
   *
   * @return the list of Applications
   */
  public List<ApplicationOverview> getApplications() {
    log.info("Retrieving all applications");
    // TODO: replace with downstream API call
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

  /**
   * Gets an Application or empty optional if not found.
   *
   * @return {@link Optional} of {@link ApplicationResponse}
   */
  public Optional<ApplicationResponse> getApplication(UUID applicationId) {
    log.info("Retrieving application");
    // TODO: replace with downstream API call
    return Optional.of(
        ApplicationResponse.builder()
            .id(applicationId)
            .individualLegalAidNumber(UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901"))
            .providerFirmId(UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901"))
            .providerOfficeId(UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901"))
            .createdAt(OffsetDateTime.now())
            .createdBy("Random User")
            .modifiedAt(OffsetDateTime.now())
            .modifiedBy("Random User")
            .build());
  }
}
