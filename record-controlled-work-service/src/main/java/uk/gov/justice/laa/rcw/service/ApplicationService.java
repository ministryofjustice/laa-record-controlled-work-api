package uk.gov.justice.laa.rcw.service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import uk.gov.justice.laa.rcw.model.Application;
import uk.gov.justice.laa.rcw.model.CreateApplicationRequestBody;
import uk.gov.justice.laa.rcw.model.CreateApplicationResponseBody;

/** Service class for handling Application requests. */
@Slf4j
@RequiredArgsConstructor
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
            .modifiedAt(OffsetDateTime.now())
            .applicationRefNumber("CW-111111")
            .build(),
        Application.builder()
            .id(UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901"))
            .name("Other Random Name")
            .modifiedAt(OffsetDateTime.now())
            .applicationRefNumber("CW-222222")
            .build());
  }

  /**
   * Create application.
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
