package uk.gov.justice.laa.rcw.controller;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.rcw.api.ApplicationsApi;
import uk.gov.justice.laa.rcw.model.ApplicationOverview;
import uk.gov.justice.laa.rcw.model.ApplicationResponse;
import uk.gov.justice.laa.rcw.service.ApplicationService;

/** Controller for handling application requests. */
@RestController
@RequiredArgsConstructor
@Slf4j
public class ApplicationController implements ApplicationsApi {

  private final ApplicationService applicationService;

  @Override
  public ResponseEntity<List<ApplicationOverview>> getApplications() {
    return ResponseEntity.ok(applicationService.getApplications());
  }

  @Override
  public ResponseEntity<ApplicationResponse> getApplication(UUID id) {
    return applicationService
        .getApplication(id)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }
}
