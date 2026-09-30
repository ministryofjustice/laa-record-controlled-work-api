package uk.gov.justice.laa.rcw.mapper;

import org.mapstruct.Mapper;
import uk.gov.justice.laa.ia.datastore.client.model.EvidenceResponse;
import uk.gov.justice.laa.rcw.model.Evidence;

/** Maps evidence models from the datastore to the RCW API. */
@Mapper(componentModel = "spring")
public interface EvidenceMapper {

  /** Maps the datastore evidence response onto the RCW API model. */
  Evidence toEvidence(EvidenceResponse evidenceResponse);
}
