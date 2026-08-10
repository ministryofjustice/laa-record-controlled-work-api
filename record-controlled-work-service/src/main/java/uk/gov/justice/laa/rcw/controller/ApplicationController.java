package uk.gov.justice.laa.rcw.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import uk.gov.justice.laa.rcw.api.ApplicationsApi;
import uk.gov.justice.laa.rcw.model.Application;
import uk.gov.justice.laa.rcw.model.ApplicationOverview;
import uk.gov.justice.laa.rcw.model.ApplicationState;
import uk.gov.justice.laa.rcw.model.CreateApplicationRequestBody;
import uk.gov.justice.laa.rcw.model.CreateApplicationResponseBody;
import uk.gov.justice.laa.rcw.model.UpdateEvidenceRequestBody;
import uk.gov.justice.laa.rcw.model.UpdateMeansDataRequestBody;
import uk.gov.justice.laa.rcw.service.ApplicationCreationService;
import uk.gov.justice.laa.rcw.service.ApplicationEvidenceService;
import uk.gov.justice.laa.rcw.service.ApplicationMeansService;
import uk.gov.justice.laa.rcw.service.ApplicationQueryService;

/** Controller for handling application requests. */
@RestController
@RequiredArgsConstructor
public class ApplicationController implements ApplicationsApi {

  private final ApplicationQueryService applicationQueryService;
  private final ApplicationMeansService applicationMeansService;
  private final ApplicationEvidenceService applicationEvidenceService;
  private final ApplicationCreationService applicationCreationService;

  @Override
  public ResponseEntity<CreateApplicationResponseBody> createApplication(
      CreateApplicationRequestBody applicationRequestBody) {
    CreateApplicationResponseBody responseBody =
        applicationCreationService.createApplication(applicationRequestBody);
    URI uri =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/api/v1/applications/{id}")
            .buildAndExpand(responseBody.getId())
            .toUri();
    return ResponseEntity.created(uri).body(responseBody);
  }

  @Override
  public ResponseEntity<List<ApplicationOverview>> getApplications(
      Integer page, Integer size, String officeId, ApplicationState status) {
    return ResponseEntity.ok(applicationQueryService.getApplications(page, size, officeId, status));
  }

  @Override
  public ResponseEntity<Application> getApplication(UUID id) {
    return applicationQueryService
        .getApplication(id)
        .map(ResponseEntity::ok)
        .orElse(ResponseEntity.notFound().build());
  }

  @Override
  public ResponseEntity<Void> updateApplicationMeans(
      UUID id, UpdateMeansDataRequestBody updateMeansDataRequestBody) {
    applicationMeansService.updateMeans(
        id, updateMeansDataRequestBody.getData(), updateMeansDataRequestBody.getResult());
    return ResponseEntity.noContent().build();
  }

  @Override
  public ResponseEntity<Void> updateApplicationEvidence(
      UUID id, UpdateEvidenceRequestBody updateEvidenceRequestBody) {
    applicationEvidenceService.updateEvidence(id, updateEvidenceRequestBody);
    return ResponseEntity.noContent().build();
  }
}
