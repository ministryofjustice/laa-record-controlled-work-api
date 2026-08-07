package uk.gov.justice.laa.rcw.mapper;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationSummary;
import uk.gov.justice.laa.rcw.model.ApplicationOverview;
import uk.gov.justice.laa.rcw.model.ApplicationState;

/** The mapper between the datastore's ApplicationSummary and the RCW ApplicationOverview. */
@Mapper(componentModel = "spring")
public interface ApplicationMapper {

  /**
   * Maps the given application summary to an application overview.
   *
   * @param applicationSummary the application summary
   * @return the application overview
   */
  @Mapping(target = "applicationRefNumber", source = "referenceNumber")
  @Mapping(target = "name", expression = "java(toName(applicationSummary))")
  ApplicationOverview toApplicationOverview(ApplicationSummary applicationSummary);

  /** Joins the client's first and last name, skipping any that are null. */
  default String toName(ApplicationSummary applicationSummary) {
    return Stream.of(
            applicationSummary.getClientFirstName(), applicationSummary.getClientLastName())
        .filter(Objects::nonNull)
        .collect(Collectors.joining(" "));
  }

  /** Maps the RCW application status to the datastore's equivalent enum. */
  default uk.gov.justice.laa.ia.datastore.client.model.ApplicationState toDatastoreApplicationState(
      ApplicationState status) {
    if (status == null) {
      return null;
    }
    return switch (status) {
      case DRAFT -> uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT;
      case COMPLETED -> uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.COMPLETED;
    };
  }
}
