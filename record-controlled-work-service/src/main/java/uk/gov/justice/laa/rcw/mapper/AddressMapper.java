package uk.gov.justice.laa.rcw.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import uk.gov.justice.laa.ia.datastore.client.model.CreateAddressCommand;
import uk.gov.justice.laa.rcw.model.Address;
import uk.gov.justice.laa.rcw.model.CreateAddressRequestBody;

/** Maps address models between the datastore and RCW API. */
@Mapper(componentModel = "spring")
public interface AddressMapper {

  /** Maps the datastore address onto the RCW API address. */
  @Mapping(target = "id", ignore = true)
  Address toAddress(uk.gov.justice.laa.ia.datastore.client.model.Address address);

  /** Maps the RCW address request onto the datastore create command. */
  CreateAddressCommand toCreateAddressCommand(CreateAddressRequestBody address);
}
