package uk.gov.justice.laa.rcw.mapper;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationSummary;
import uk.gov.justice.laa.ia.datastore.client.model.EditApplicationCommand;
import uk.gov.justice.laa.ia.datastore.client.model.StartApplicationCommand;
import uk.gov.justice.laa.rcw.model.Application;
import uk.gov.justice.laa.rcw.model.ApplicationOverview;
import uk.gov.justice.laa.rcw.model.ApplicationState;
import uk.gov.justice.laa.rcw.model.CreateApplicationRequestBody;
import uk.gov.justice.laa.rcw.model.PriorLegalAid;
import uk.gov.justice.laa.rcw.model.UpdateApplicationDetailsRequestBody;

/** The mapper between the datastore's application models and the RCW API's own models. */
@Mapper(
    componentModel = "spring",
    uses = {
      ClientDetailsMapper.class,
      DeclarationMapper.class,
      EligibilityMapper.class,
      EvidenceMapper.class,
      ScopingQuestionsMapper.class,
      JsonNullableMapper.class
    },
    injectionStrategy = InjectionStrategy.CONSTRUCTOR)
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

  /**
   * Maps the datastore's application response to the RCW API's application.
   *
   * @param applicationResponse the datastore application response
   * @return the RCW API application
   */
  @Mapping(target = "clientDetails", source = "client")
  @Mapping(target = "eligibility", source = "eligibilityResult")
  @Mapping(target = "evidence", source = "evidence")
  @Mapping(target = "meansAssessmentId", ignore = true)
  @Mapping(target = "applicationRefNumber", source = "referenceNumber")
  Application toApplication(ApplicationResponse applicationResponse);

  /** Joins the client's first and last name, skipping any that are null. */
  default String toName(ApplicationSummary applicationSummary) {
    return Stream.of(
            applicationSummary.getClientFirstName(), applicationSummary.getClientLastName())
        .filter(Objects::nonNull)
        .collect(Collectors.joining(" "));
  }

  /** Maps the RCW application status to the datastore's equivalent enum. */
  uk.gov.justice.laa.ia.datastore.client.model.ApplicationState toDatastoreApplicationState(
      ApplicationState status);

  /** Maps the RCW create request to the datastore start-application command. */
  @Mapping(target = "client", source = "clientDetails")
  @Mapping(target = "ufn", ignore = true)
  @Mapping(
      target = "applicationType",
      expression = "java(StartApplicationCommand.ApplicationTypeEnum.RCW)")
  StartApplicationCommand toStartApplicationCommand(
      CreateApplicationRequestBody createApplicationRequestBody);

  /**
   * Maps the validated details snapshot to a sparse datastore edit command.
   *
   * @param request the complete validated details snapshot
   * @param version the caller's version precondition
   * @return the sparse datastore command
   */
  @BeanMapping(
      ignoreByDefault = true,
      builder = @Builder(disableBuilder = true),
      unmappedSourcePolicy = ReportingPolicy.ERROR,
      ignoreUnmappedSourceProperties = "legalAidLast6Months")
  @Mapping(target = "eTag", source = "version")
  @Mapping(target = "clientDetails", source = "request.clientDetails")
  @Mapping(
      target = "reasonForReapplication_JsonNullable",
      source = "request.reasonForReapplication",
      qualifiedByName = "toPresentJsonNullable")
  @Mapping(
      target = "ecfFlag_JsonNullable",
      source = "request.ecfFlag",
      qualifiedByName = "toPresentJsonNullable")
  @Mapping(
      target = "scopingQuestions",
      source = "request.priorLegalAid",
      qualifiedByName = "toPriorLegalAidScopingQuestions")
  @Mapping(target = "ufn", ignore = true)
  @Mapping(target = "laaReference", ignore = true)
  @Mapping(target = "meansAssessmentRequired", ignore = true)
  @Mapping(target = "typeOfNonMeans", ignore = true)
  @Mapping(target = "contribution", ignore = true)
  @Mapping(target = "determinationId", ignore = true)
  @Mapping(target = "declaration", ignore = true)
  @Mapping(target = "evidence", ignore = true)
  EditApplicationCommand toEditApplicationCommand(
      UpdateApplicationDetailsRequestBody request, long version);

  /** Converts the reapplication answer into datastore scoping data. */
  @Named("toPriorLegalAidScopingQuestions")
  default Map<String, Object> toPriorLegalAidScopingQuestions(PriorLegalAid priorLegalAid) {
    return Map.of("priorLegalAid", priorLegalAid.getValue());
  }

  /** Maps datastore application state back to the RCW application state. */
  ApplicationState toApplicationState(
      uk.gov.justice.laa.ia.datastore.client.model.ApplicationState status);
}
