package uk.gov.justice.laa.rcw.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationSummary;
import uk.gov.justice.laa.rcw.model.ApplicationOverview;
import uk.gov.justice.laa.rcw.model.ApplicationState;

class ApplicationMapperTest {

  private static final UUID APPLICATION_ID =
      UUID.fromString("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
  private static final String REFERENCE_NUMBER = "CW-111111";
  private static final OffsetDateTime MODIFIED_AT = OffsetDateTime.now();

  private final ApplicationMapper applicationMapper = new ApplicationMapperImpl();

  @Test
  void shouldMapApplicationSummaryToApplicationOverview() {
    ApplicationSummary applicationSummary =
        ApplicationSummary.builder()
            .id(APPLICATION_ID)
            .clientFirstName("Joe")
            .clientLastName("Bloggs")
            .referenceNumber(REFERENCE_NUMBER)
            .modifiedAt(MODIFIED_AT)
            .build();

    ApplicationOverview result = applicationMapper.toApplicationOverview(applicationSummary);

    assertThat(result.getId()).isEqualTo(APPLICATION_ID);
    assertThat(result.getName()).isEqualTo("Joe Bloggs");
    assertThat(result.getApplicationRefNumber()).isEqualTo(REFERENCE_NUMBER);
    assertThat(result.getModifiedAt()).isEqualTo(MODIFIED_AT);
  }

  @Test
  void shouldMapApplicationSummaryToApplicationOverview_whenClientFirstNameIsNull() {
    ApplicationSummary applicationSummary =
        ApplicationSummary.builder()
            .id(APPLICATION_ID)
            .clientFirstName(null)
            .clientLastName("Bloggs")
            .referenceNumber(REFERENCE_NUMBER)
            .modifiedAt(MODIFIED_AT)
            .build();

    ApplicationOverview result = applicationMapper.toApplicationOverview(applicationSummary);

    assertThat(result.getName()).isEqualTo("Bloggs");
  }

  @Test
  void shouldMapApplicationSummaryToApplicationOverview_whenClientNamesAreNull() {
    ApplicationSummary applicationSummary =
        ApplicationSummary.builder()
            .id(APPLICATION_ID)
            .clientFirstName(null)
            .clientLastName(null)
            .referenceNumber(REFERENCE_NUMBER)
            .modifiedAt(MODIFIED_AT)
            .build();

    ApplicationOverview result = applicationMapper.toApplicationOverview(applicationSummary);

    assertThat(result.getName()).isEmpty();
  }

  @Test
  void shouldMapDraftStatusToDatastoreApplicationState() {
    assertThat(applicationMapper.toDatastoreApplicationState(ApplicationState.DRAFT))
        .isEqualTo(uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT);
  }

  @Test
  void shouldMapCompletedStatusToDatastoreApplicationState() {
    assertThat(applicationMapper.toDatastoreApplicationState(ApplicationState.COMPLETED))
        .isEqualTo(uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.COMPLETED);
  }

  @Test
  void shouldMapNullStatusToNull() {
    assertThat(applicationMapper.toDatastoreApplicationState(null)).isNull();
  }
}
