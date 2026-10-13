package uk.gov.justice.laa.rcw.mapper;

import java.util.List;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.openapitools.jackson.nullable.JsonNullable;
import uk.gov.justice.laa.ia.datastore.client.model.EligibilityResult;
import uk.gov.justice.laa.rcw.model.Eligibility;
import uk.gov.justice.laa.rcw.model.EligibilityData;
import uk.gov.justice.laa.rcw.model.EligibilityIndication;

/** Maps eligibility and means-data models between the datastore and RCW API. */
@Mapper(
    componentModel = "spring",
    uses = JsonNullableMapper.class,
    injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface EligibilityMapper {

  /** Maps the datastore eligibility result onto the RCW API model. */
  @Mapping(target = "data._apiResponse", ignore = true)
  Eligibility toEligibility(EligibilityResult eligibilityResult);

  /** Maps RCW eligibility data onto the datastore's means-data command payload. */
  @Mapping(target = "_apiResponse", ignore = true)
  uk.gov.justice.laa.ia.datastore.client.model.EligibilityData toDatastoreMeansData(
      EligibilityData data);

  /** Maps one RCW eligibility property onto the datastore equivalent. */
  uk.gov.justice.laa.ia.datastore.client.model.EligibilityDataProperty toDatastoreProperty(
      uk.gov.justice.laa.rcw.model.EligibilityDataProperty property);

  /** Wraps a mapped property list for a datastore {@code JsonNullable} list property. */
  default JsonNullable<List<uk.gov.justice.laa.ia.datastore.client.model.EligibilityDataProperty>>
      toJsonNullableProperties(
          List<uk.gov.justice.laa.rcw.model.EligibilityDataProperty> properties) {
    return properties == null
        ? JsonNullable.undefined()
        : JsonNullable.of(properties.stream().map(this::toDatastoreProperty).toList());
  }

  /** Maps one RCW bank account onto the datastore equivalent. */
  uk.gov.justice.laa.ia.datastore.client.model.EligibilityDataBankAccount toDatastoreBankAccount(
      uk.gov.justice.laa.rcw.model.EligibilityDataBankAccount bankAccount);

  /** Wraps a mapped bank account list for a datastore {@code JsonNullable} list property. */
  default JsonNullable<
          List<uk.gov.justice.laa.ia.datastore.client.model.EligibilityDataBankAccount>>
      toJsonNullableBankAccounts(
          List<uk.gov.justice.laa.rcw.model.EligibilityDataBankAccount> bankAccounts) {
    return bankAccounts == null
        ? JsonNullable.undefined()
        : JsonNullable.of(bankAccounts.stream().map(this::toDatastoreBankAccount).toList());
  }

  /** Maps one RCW benefit onto the datastore equivalent. */
  uk.gov.justice.laa.ia.datastore.client.model.EligibilityDataBenefit toDatastoreBenefit(
      uk.gov.justice.laa.rcw.model.EligibilityDataBenefit benefit);

  /** Wraps a mapped benefit list for a datastore {@code JsonNullable} list property. */
  default JsonNullable<List<uk.gov.justice.laa.ia.datastore.client.model.EligibilityDataBenefit>>
      toJsonNullableBenefits(List<uk.gov.justice.laa.rcw.model.EligibilityDataBenefit> benefits) {
    return benefits == null
        ? JsonNullable.undefined()
        : JsonNullable.of(benefits.stream().map(this::toDatastoreBenefit).toList());
  }

  /** Maps one RCW income onto the datastore equivalent. */
  uk.gov.justice.laa.ia.datastore.client.model.EligibilityDataIncome toDatastoreIncome(
      uk.gov.justice.laa.rcw.model.EligibilityDataIncome income);

  /** Wraps a mapped income list for a datastore {@code JsonNullable} list property. */
  default JsonNullable<List<uk.gov.justice.laa.ia.datastore.client.model.EligibilityDataIncome>>
      toJsonNullableIncomes(List<uk.gov.justice.laa.rcw.model.EligibilityDataIncome> incomes) {
    return incomes == null
        ? JsonNullable.undefined()
        : JsonNullable.of(incomes.stream().map(this::toDatastoreIncome).toList());
  }

  /** Maps one RCW dependant income onto the datastore equivalent. */
  uk.gov.justice.laa.ia.datastore.client.model.EligibilityDataDependantIncome
      toDatastoreDependantIncome(
          uk.gov.justice.laa.rcw.model.EligibilityDataDependantIncome dependantIncome);

  /** Wraps a mapped dependant income list for a datastore {@code JsonNullable} list property. */
  default JsonNullable<
          List<uk.gov.justice.laa.ia.datastore.client.model.EligibilityDataDependantIncome>>
      toJsonNullableDependantIncomes(
          List<uk.gov.justice.laa.rcw.model.EligibilityDataDependantIncome> dependantIncomes) {
    return dependantIncomes == null
        ? JsonNullable.undefined()
        : JsonNullable.of(dependantIncomes.stream().map(this::toDatastoreDependantIncome).toList());
  }

  /** Maps one RCW vehicle onto the datastore equivalent. */
  uk.gov.justice.laa.ia.datastore.client.model.EligibilityDataVehicle toDatastoreVehicle(
      uk.gov.justice.laa.rcw.model.EligibilityDataVehicle vehicle);

  /** Wraps a mapped vehicle list for a datastore {@code JsonNullable} list property. */
  default JsonNullable<List<uk.gov.justice.laa.ia.datastore.client.model.EligibilityDataVehicle>>
      toJsonNullableVehicles(List<uk.gov.justice.laa.rcw.model.EligibilityDataVehicle> vehicles) {
    return vehicles == null
        ? JsonNullable.undefined()
        : JsonNullable.of(vehicles.stream().map(this::toDatastoreVehicle).toList());
  }

  /** Maps the RCW early-result snapshot onto the datastore equivalent. */
  uk.gov.justice.laa.ia.datastore.client.model.EligibilityDataEarlyResult toDatastoreEarlyResult(
      uk.gov.justice.laa.rcw.model.EligibilityDataEarlyResult earlyResult);

  /** Wraps an early-result snapshot for the datastore's {@code JsonNullable} property. */
  default JsonNullable<uk.gov.justice.laa.ia.datastore.client.model.EligibilityDataEarlyResult>
      toJsonNullableEarlyResult(
          uk.gov.justice.laa.rcw.model.EligibilityDataEarlyResult earlyResult) {
    return earlyResult == null
        ? JsonNullable.undefined()
        : JsonNullable.of(toDatastoreEarlyResult(earlyResult));
  }

  /** Maps the RCW eligibility indication onto the datastore enum. */
  uk.gov.justice.laa.ia.datastore.client.model.EligibilityIndication
      toDatastoreEligibilityIndication(EligibilityIndication eligibilityIndication);

  /** Maps the datastore eligibility indication onto the RCW enum. */
  EligibilityIndication toEligibilityIndication(
      uk.gov.justice.laa.ia.datastore.client.model.EligibilityIndication eligibilityIndication);
}
