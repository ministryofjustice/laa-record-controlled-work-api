package uk.gov.justice.laa.rcw.mapper;

import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.InjectionStrategy;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;
import uk.gov.justice.laa.ia.datastore.client.model.CreateAddressCommand;
import uk.gov.justice.laa.ia.datastore.client.model.PatchAddressData;
import uk.gov.justice.laa.rcw.model.Address;
import uk.gov.justice.laa.rcw.model.CreateAddressRequestBody;
import uk.gov.justice.laa.rcw.model.UpdateAddressRequestBody;

/** Maps address models between the datastore and RCW API. */
@Mapper(
    componentModel = "spring",
    uses = JsonNullableMapper.class,
    injectionStrategy = InjectionStrategy.CONSTRUCTOR)
public interface AddressMapper {

  /** Maps the datastore address onto the RCW API address. */
  @Mapping(target = "id", ignore = true)
  Address toAddress(uk.gov.justice.laa.ia.datastore.client.model.Address address);

  /** Maps the RCW address request onto the datastore create command. */
  CreateAddressCommand toCreateAddressCommand(CreateAddressRequestBody address);

  /** Maps editable address fields, preserving explicitly nullable values. */
  @BeanMapping(
      ignoreByDefault = true,
      builder = @Builder(disableBuilder = true),
      unmappedSourcePolicy = ReportingPolicy.ERROR)
  @Mapping(target = "addressLine1", source = "addressLine1")
  @Mapping(
      target = "addressLine2_JsonNullable",
      source = "addressLine2",
      qualifiedByName = "toPresentJsonNullable")
  @Mapping(
      target = "addressLine3_JsonNullable",
      source = "addressLine3",
      qualifiedByName = "toPresentJsonNullable")
  @Mapping(
      target = "addressLine4_JsonNullable",
      source = "addressLine4",
      qualifiedByName = "toPresentJsonNullable")
  @Mapping(
      target = "townOrCity_JsonNullable",
      source = "townOrCity",
      qualifiedByName = "toPresentJsonNullable")
  @Mapping(
      target = "postCode_JsonNullable",
      source = "postCode",
      qualifiedByName = "toPresentJsonNullable")
  @Mapping(
      target = "county_JsonNullable",
      source = "county",
      qualifiedByName = "toPresentJsonNullable")
  @Mapping(target = "country", source = "country")
  PatchAddressData toPatchAddressData(UpdateAddressRequestBody address);
}
