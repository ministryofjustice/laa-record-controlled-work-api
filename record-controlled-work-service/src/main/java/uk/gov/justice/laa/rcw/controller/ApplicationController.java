package uk.gov.justice.laa.rcw.controller;

import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.justice.laa.rcw.api.ApplicationsApi;
import uk.gov.justice.laa.rcw.model.Application;
import uk.gov.justice.laa.rcw.model.CreateApplicationRequestBody;
import uk.gov.justice.laa.rcw.model.CreateApplicationResponseBody;
import uk.gov.justice.laa.rcw.service.ApplicationService;

/** Controller for handling application requests. */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ApplicationController implements ApplicationsApi {

  private final ApplicationService applicationService;

  @Override
  public ResponseEntity<CreateApplicationResponseBody> createApplication(
      CreateApplicationRequestBody applicationRequestBody) {
    log.info("POST /api/v1/createApplication");
    CreateApplicationResponseBody responseBody =
        applicationService.createApplication(applicationRequestBody);
    URI uri =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/api/v1/applications/{id}")
            .buildAndExpand(responseBody.getId())
            .toUri();
    return ResponseEntity.created(uri).body(responseBody);
  }

  @Override
  public ResponseEntity<List<Application>> getApplications() {
    return ResponseEntity.ok(applicationService.getApplications());
  }
}
