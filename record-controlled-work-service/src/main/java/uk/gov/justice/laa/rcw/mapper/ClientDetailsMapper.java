package uk.gov.justice.laa.rcw.mapper;

import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.openapitools.jackson.nullable.JsonNullable;
import uk.gov.justice.laa.ia.datastore.client.model.CreateClientCommand;
import uk.gov.justice.laa.ia.datastore.client.model.PatchAddressData;
import uk.gov.justice.laa.ia.datastore.client.model.PatchClientDetailsData;
import uk.gov.justice.laa.rcw.model.ClientDetails;
import uk.gov.justice.laa.rcw.model.CreateClientDetailsRequestBody;
import uk.gov.justice.laa.rcw.model.UpdateAddressRequestBody;
import uk.gov.justice.laa.rcw.model.UpdateClientDetailsRequestBody;

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

  /**
   * Maps client fields for a sparse edit, explicitly including nullable clears.
   *
   * @param clientDetails the validated client details
   * @return the datastore patch client details
   */
  default PatchClientDetailsData toPatchClientDetailsData(
      UpdateClientDetailsRequestBody clientDetails) {
    PatchClientDetailsData result =
        new PatchClientDetailsData()
            .firstName(clientDetails.getFirstName())
            .lastName(clientDetails.getLastName())
            .dateOfBirth(clientDetails.getDateOfBirth())
            .noFixedAbode(!clientDetails.getHasFixedAddress());
    result.setNiNumber_JsonNullable(JsonNullable.of(clientDetails.getNiNumber()));
    result.setAddress_JsonNullable(JsonNullable.of(toPatchAddressData(clientDetails.getAddress())));
    return result;
  }

  private static PatchAddressData toPatchAddressData(UpdateAddressRequestBody address) {
    if (address == null) {
      return null;
    }
    PatchAddressData result =
        new PatchAddressData()
            .addressLine1(address.getAddressLine1())
            .country(address.getCountry());
    result.setAddressLine2_JsonNullable(JsonNullable.of(address.getAddressLine2()));
    result.setAddressLine3_JsonNullable(JsonNullable.of(address.getAddressLine3()));
    result.setAddressLine4_JsonNullable(JsonNullable.of(address.getAddressLine4()));
    result.setTownOrCity_JsonNullable(JsonNullable.of(address.getTownOrCity()));
    result.setPostCode_JsonNullable(JsonNullable.of(address.getPostCode()));
    result.setCounty_JsonNullable(JsonNullable.of(address.getCounty()));
    return result;
  }

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
