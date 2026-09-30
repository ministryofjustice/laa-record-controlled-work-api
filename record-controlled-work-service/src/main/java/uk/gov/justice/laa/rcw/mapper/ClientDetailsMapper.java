package uk.gov.justice.laa.rcw.mapper;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import uk.gov.justice.laa.ia.datastore.client.model.CreateClientCommand;
import uk.gov.justice.laa.rcw.model.ClientDetails;
import uk.gov.justice.laa.rcw.model.CreateClientDetailsRequestBody;

/** Maps client details between the datastore and RCW API. */
@Mapper(
    componentModel = "spring",
    uses = AddressMapper.class,
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
