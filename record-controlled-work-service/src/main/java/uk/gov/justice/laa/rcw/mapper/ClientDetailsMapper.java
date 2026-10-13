package uk.gov.justice.laa.rcw.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;
import uk.gov.justice.laa.ia.datastore.client.model.CreateClientCommand;
import uk.gov.justice.laa.ia.datastore.client.model.PatchClientDetailsData;
import uk.gov.justice.laa.rcw.model.ClientDetails;
import uk.gov.justice.laa.rcw.model.CreateClientDetailsRequestBody;
import uk.gov.justice.laa.rcw.model.UpdateClientDetailsRequestBody;

/** Maps client details between the datastore and RCW API. */
@Mapper(
    componentModel = "spring",
    uses = {AddressMapper.class, JsonNullableMapper.class},
    injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface ClientDetailsMapper {

  /** Maps datastore client details to the RCW API, inverting {@code noFixedAbode}. */
  @Mapping(target = "id", ignore = true)
  @Mapping(
      target = "hasFixedAddress",
      source = "noFixedAbode",
      qualifiedByName = "toHasFixedAddress")
  ClientDetails toClientDetails(
      uk.gov.justice.laa.ia.datastore.client.model.ClientDetails clientDetails);

  /** Maps RCW client details to the datastore create command. */
  @Mapping(target = "nationalInsuranceNumber", source = "niNumber")
  @Mapping(target = "noFixedAbode", source = "hasFixedAddress", qualifiedByName = "toNoFixedAbode")
  @Mapping(target = "createAddressCommand", source = "address")
  CreateClientCommand toCreateClientCommand(CreateClientDetailsRequestBody clientDetails);

  /**
   * Maps client fields for a sparse edit, explicitly including nullable clears.
   *
   * @param clientDetails the validated client details
   * @return the datastore patch client details
   */
  @BeanMapping(
      ignoreByDefault = true,
      builder = @Builder(disableBuilder = true),
      unmappedSourcePolicy = ReportingPolicy.ERROR)
  @Mapping(target = "firstName", source = "firstName")
  @Mapping(target = "lastName", source = "lastName")
  @Mapping(target = "dateOfBirth", source = "dateOfBirth")
  @Mapping(
      target = "niNumber_JsonNullable",
      source = "niNumber",
      qualifiedByName = "toPresentJsonNullable")
  @Mapping(target = "noFixedAbode", source = "hasFixedAddress", qualifiedByName = "toNoFixedAbode")
  @Mapping(
      target = "address_JsonNullable",
      source = "address",
      qualifiedByName = "toPresentJsonNullable")
  PatchClientDetailsData toPatchClientDetailsData(UpdateClientDetailsRequestBody clientDetails);

  /** Inverts {@code noFixedAbode} to {@code hasFixedAddress}, preserving unknown values. */
  @Named("toHasFixedAddress")
  default Boolean toHasFixedAddress(Boolean noFixedAbode) {
    return noFixedAbode == null ? null : !noFixedAbode;
  }

  /** Inverts {@code hasFixedAddress}; an unknown value means no fixed abode. */
  @Named("toNoFixedAbode")
  default Boolean toNoFixedAbode(Boolean hasFixedAddress) {
    return !Boolean.TRUE.equals(hasFixedAddress);
  }
}
