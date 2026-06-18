package uk.gov.justice.laa.rcw.controller;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.rcw.api.ApplicationsApi;
import uk.gov.justice.laa.rcw.model.Application;
import uk.gov.justice.laa.rcw.model.ApplicationRequestBody;
import uk.gov.justice.laa.rcw.service.ApplicationService;

/** Controller for handling application requests. */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ApplicationController implements ApplicationsApi {

  private final ApplicationService applicationService;

  @Override
  public ResponseEntity<Void> createApplication(ApplicationRequestBody applicationRequestBody) {
    log.info("POST /api/v1/createApplication");
    applicationService.createApplication(applicationRequestBody);
    return null;
  }

  @Override
  public ResponseEntity<List<Application>> getApplications() {
    return ResponseEntity.ok(applicationService.getApplications());
  }
}
