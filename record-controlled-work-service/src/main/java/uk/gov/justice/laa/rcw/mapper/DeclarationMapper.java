package uk.gov.justice.laa.rcw.mapper;

import org.mapstruct.Mapper;
import uk.gov.justice.laa.ia.datastore.client.model.DeclarationResponse;
import uk.gov.justice.laa.rcw.model.Declaration;

/** Maps declaration models from the datastore to the RCW API. */
@Mapper(componentModel = "spring")
public interface DeclarationMapper {

  /** Maps the datastore declaration response onto the RCW API model. */
  Declaration toDeclaration(DeclarationResponse declarationResponse);
}
