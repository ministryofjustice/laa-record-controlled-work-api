package uk.gov.justice.laa.rcw.mapper;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.openapitools.jackson.nullable.JsonNullable;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationSummary;
import uk.gov.justice.laa.ia.datastore.client.model.CreateAddressCommand;
import uk.gov.justice.laa.ia.datastore.client.model.CreateClientCommand;
import uk.gov.justice.laa.ia.datastore.client.model.DeclarationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.EligibilityResult;
import uk.gov.justice.laa.ia.datastore.client.model.EvidenceResponse;
import uk.gov.justice.laa.ia.datastore.client.model.StartApplicationCommand;
import uk.gov.justice.laa.rcw.model.Address;
import uk.gov.justice.laa.rcw.model.Application;
import uk.gov.justice.laa.rcw.model.ApplicationOverview;
import uk.gov.justice.laa.rcw.model.ApplicationState;
import uk.gov.justice.laa.rcw.model.ClientDetails;
import uk.gov.justice.laa.rcw.model.CreateAddressRequestBody;
import uk.gov.justice.laa.rcw.model.CreateApplicationRequestBody;
import uk.gov.justice.laa.rcw.model.CreateClientDetailsRequestBody;
import uk.gov.justice.laa.rcw.model.Declaration;
import uk.gov.justice.laa.rcw.model.Eligibility;
import uk.gov.justice.laa.rcw.model.EligibilityData;
import uk.gov.justice.laa.rcw.model.EligibilityIndication;
import uk.gov.justice.laa.rcw.model.Evidence;

/** The mapper between the datastore's application models and the RCW API's own models. */
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

  /** Maps the datastore's client details onto the RCW API's, inverting `noFixedAbode`. */
  @Mapping(target = "id", ignore = true)
  @Mapping(
      target = "hasFixedAddress",
      source = "noFixedAbode",
      qualifiedByName = "toHasFixedAddress")
  ClientDetails toClientDetails(
      uk.gov.justice.laa.ia.datastore.client.model.ClientDetails clientDetails);

  /** Maps the datastore's address onto the RCW API's. */
  @Mapping(target = "id", ignore = true)
  Address toAddress(uk.gov.justice.laa.ia.datastore.client.model.Address address);

  /** Maps the datastore's declaration response onto the RCW API's declaration. */
  Declaration toDeclaration(DeclarationResponse declarationResponse);

  /** Maps the datastore's eligibility result onto the RCW API's eligibility. */
  @Mapping(target = "data", source = "data", qualifiedByName = "omitNullProperties")
  Eligibility toEligibility(EligibilityResult eligibilityResult);

  /** Removes null-valued object properties recursively from eligibility data. */
  @Named("omitNullProperties")
  default Object omitNullProperties(Object value) {
    return EligibilityDataSanitizer.omitNullProperties(value);
  }

  /** Maps the datastore's evidence response onto the RCW API's evidence. */
  Evidence toEvidence(EvidenceResponse evidenceResponse);

  /** Maps the RCW eligibility data onto the datastore's means-data command payload. */
  @Mapping(target = "_apiResponse", ignore = true) // cached CFE response, not sent on write
  uk.gov.justice.laa.ia.datastore.client.model.EligibilityData toDatastoreMeansData(
      EligibilityData data);

  /** Wraps a mapped value for the datastore's {@link JsonNullable}-typed properties. */
  default <T> JsonNullable<T> toJsonNullable(T value) {
    return JsonNullable.of(value);
  }

  /** Maps a single RCW eligibility property onto the datastore's equivalent. */
  uk.gov.justice.laa.ia.datastore.client.model.EligibilityDataProperty toDatastoreProperty(
      uk.gov.justice.laa.rcw.model.EligibilityDataProperty property);

  /**
   * Wraps a mapped property list for the datastore's {@code JsonNullable}-typed list properties.
   */
  default JsonNullable<List<uk.gov.justice.laa.ia.datastore.client.model.EligibilityDataProperty>>
      toJsonNullableProperties(
          List<uk.gov.justice.laa.rcw.model.EligibilityDataProperty> properties) {
    return properties == null
        ? JsonNullable.undefined()
        : JsonNullable.of(properties.stream().map(this::toDatastoreProperty).toList());
  }

  /** Maps a single RCW bank account onto the datastore's equivalent. */
  uk.gov.justice.laa.ia.datastore.client.model.EligibilityDataBankAccount toDatastoreBankAccount(
      uk.gov.justice.laa.rcw.model.EligibilityDataBankAccount bankAccount);

  /**
   * Wraps a mapped bank account list for the datastore's {@code JsonNullable}-typed list
   * properties.
   */
  default JsonNullable<
          List<uk.gov.justice.laa.ia.datastore.client.model.EligibilityDataBankAccount>>
      toJsonNullableBankAccounts(
          List<uk.gov.justice.laa.rcw.model.EligibilityDataBankAccount> bankAccounts) {
    return bankAccounts == null
        ? JsonNullable.undefined()
        : JsonNullable.of(bankAccounts.stream().map(this::toDatastoreBankAccount).toList());
  }

  /** Maps a single RCW benefit onto the datastore's equivalent. */
  uk.gov.justice.laa.ia.datastore.client.model.EligibilityDataBenefit toDatastoreBenefit(
      uk.gov.justice.laa.rcw.model.EligibilityDataBenefit benefit);

  /** Wraps a mapped benefit list for the datastore's {@code JsonNullable}-typed list properties. */
  default JsonNullable<List<uk.gov.justice.laa.ia.datastore.client.model.EligibilityDataBenefit>>
      toJsonNullableBenefits(List<uk.gov.justice.laa.rcw.model.EligibilityDataBenefit> benefits) {
    return benefits == null
        ? JsonNullable.undefined()
        : JsonNullable.of(benefits.stream().map(this::toDatastoreBenefit).toList());
  }

  /** Maps a single RCW income onto the datastore's equivalent. */
  uk.gov.justice.laa.ia.datastore.client.model.EligibilityDataIncome toDatastoreIncome(
      uk.gov.justice.laa.rcw.model.EligibilityDataIncome income);

  /** Wraps a mapped income list for the datastore's {@code JsonNullable}-typed list properties. */
  default JsonNullable<List<uk.gov.justice.laa.ia.datastore.client.model.EligibilityDataIncome>>
      toJsonNullableIncomes(List<uk.gov.justice.laa.rcw.model.EligibilityDataIncome> incomes) {
    return incomes == null
        ? JsonNullable.undefined()
        : JsonNullable.of(incomes.stream().map(this::toDatastoreIncome).toList());
  }

  /** Maps a single RCW dependant income onto the datastore's equivalent. */
  uk.gov.justice.laa.ia.datastore.client.model.EligibilityDataDependantIncome
      toDatastoreDependantIncome(
          uk.gov.justice.laa.rcw.model.EligibilityDataDependantIncome dependantIncome);

  /**
   * Wraps a mapped dependant income list for the datastore's {@code JsonNullable}-typed list
   * properties.
   */
  default JsonNullable<
          List<uk.gov.justice.laa.ia.datastore.client.model.EligibilityDataDependantIncome>>
      toJsonNullableDependantIncomes(
          List<uk.gov.justice.laa.rcw.model.EligibilityDataDependantIncome> dependantIncomes) {
    return dependantIncomes == null
        ? JsonNullable.undefined()
        : JsonNullable.of(dependantIncomes.stream().map(this::toDatastoreDependantIncome).toList());
  }

  /** Maps a single RCW vehicle onto the datastore's equivalent. */
  uk.gov.justice.laa.ia.datastore.client.model.EligibilityDataVehicle toDatastoreVehicle(
      uk.gov.justice.laa.rcw.model.EligibilityDataVehicle vehicle);

  /** Wraps a mapped vehicle list for the datastore's {@code JsonNullable}-typed list properties. */
  default JsonNullable<List<uk.gov.justice.laa.ia.datastore.client.model.EligibilityDataVehicle>>
      toJsonNullableVehicles(List<uk.gov.justice.laa.rcw.model.EligibilityDataVehicle> vehicles) {
    return vehicles == null
        ? JsonNullable.undefined()
        : JsonNullable.of(vehicles.stream().map(this::toDatastoreVehicle).toList());
  }

  /** Maps the RCW early-result snapshot onto the datastore's equivalent. */
  uk.gov.justice.laa.ia.datastore.client.model.EligibilityDataEarlyResult toDatastoreEarlyResult(
      uk.gov.justice.laa.rcw.model.EligibilityDataEarlyResult earlyResult);

  /**
   * Wraps a mapped early-result snapshot for the datastore's {@code JsonNullable}-typed property.
   */
  default JsonNullable<uk.gov.justice.laa.ia.datastore.client.model.EligibilityDataEarlyResult>
      toJsonNullableEarlyResult(
          uk.gov.justice.laa.rcw.model.EligibilityDataEarlyResult earlyResult) {
    return earlyResult == null
        ? JsonNullable.undefined()
        : JsonNullable.of(toDatastoreEarlyResult(earlyResult));
  }

  /** Inverts `noFixedAbode` to `hasFixedAddress`, preserving null (unknown). */
  @Named("toHasFixedAddress")
  default Boolean toHasFixedAddress(Boolean noFixedAbode) {
    return noFixedAbode == null ? null : !noFixedAbode;
  }

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

  /** Maps the RCW eligibility indication to the datastore's equivalent enum. */
  uk.gov.justice.laa.ia.datastore.client.model.EligibilityIndication
      toDatastoreEligibilityIndication(EligibilityIndication eligibilityIndication);

  /** Maps datastore eligibility indication back to the RCW eligibility indication. */
  EligibilityIndication toEligibilityIndication(
      uk.gov.justice.laa.ia.datastore.client.model.EligibilityIndication eligibilityIndication);

  /** Maps the RCW create request to the datastore start-application command. */
  @Mapping(target = "client", source = "clientDetails")
  @Mapping(
      target = "applicationType",
      expression = "java(StartApplicationCommand.ApplicationTypeEnum.RCW)")
  StartApplicationCommand toStartApplicationCommand(
      CreateApplicationRequestBody createApplicationRequestBody);

  /** Maps the RCW client details to the datastore create client command. */
  @Mapping(target = "nationalInsuranceNumber", source = "niNumber")
  @Mapping(
      target = "noFixedAbode",
      expression = "java(!Boolean.TRUE.equals(clientDetails.getHasFixedAddress()))")
  @Mapping(target = "createAddressCommand", source = "address")
  CreateClientCommand toCreateClientCommand(CreateClientDetailsRequestBody clientDetails);

  /** Maps the RCW address to the datastore create address command. */
  CreateAddressCommand toCreateAddressCommand(CreateAddressRequestBody address);

  /** Maps datastore application state back to the RCW application state. */
  ApplicationState toApplicationState(
      uk.gov.justice.laa.ia.datastore.client.model.ApplicationState status);
}
