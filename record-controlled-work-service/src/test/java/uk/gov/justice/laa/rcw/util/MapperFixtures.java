package uk.gov.justice.laa.rcw.util;

import uk.gov.justice.laa.rcw.mapper.AddressMapper;
import uk.gov.justice.laa.rcw.mapper.AddressMapperImpl;
import uk.gov.justice.laa.rcw.mapper.ApplicationMapper;
import uk.gov.justice.laa.rcw.mapper.ApplicationMapperImpl;
import uk.gov.justice.laa.rcw.mapper.ClientDetailsMapper;
import uk.gov.justice.laa.rcw.mapper.ClientDetailsMapperImpl;
import uk.gov.justice.laa.rcw.mapper.DeclarationMapperImpl;
import uk.gov.justice.laa.rcw.mapper.EligibilityMapper;
import uk.gov.justice.laa.rcw.mapper.EligibilityMapperImpl;
import uk.gov.justice.laa.rcw.mapper.EvidenceMapperImpl;
import uk.gov.justice.laa.rcw.mapper.JsonNullableMapper;
import uk.gov.justice.laa.rcw.mapper.JsonNullableMapperImpl;
import uk.gov.justice.laa.rcw.mapper.ScopingQuestionsMapperImpl;

/** Constructs generated mapper graphs for unit tests. */
public final class MapperFixtures {

  private MapperFixtures() {}

  /** Creates a JSON nullable mapper. */
  public static JsonNullableMapper jsonNullableMapper() {
    return new JsonNullableMapperImpl();
  }

  /** Creates an address mapper with its nullable dependency. */
  public static AddressMapper addressMapper() {
    return new AddressMapperImpl(jsonNullableMapper());
  }

  /** Creates a client details mapper with its nested dependencies. */
  public static ClientDetailsMapper clientDetailsMapper() {
    JsonNullableMapper nullableMapper = jsonNullableMapper();
    return new ClientDetailsMapperImpl(new AddressMapperImpl(nullableMapper), nullableMapper);
  }

  /** Creates an eligibility mapper with its nullable dependency. */
  public static EligibilityMapper eligibilityMapper() {
    return eligibilityMapper(jsonNullableMapper());
  }

  /** Creates an eligibility mapper using the supplied nullable mapper. */
  public static EligibilityMapper eligibilityMapper(JsonNullableMapper nullableMapper) {
    return new EligibilityMapperImpl(nullableMapper);
  }

  /** Creates the application mapper with its generated dependencies. */
  public static ApplicationMapper applicationMapper() {
    JsonNullableMapper nullableMapper = jsonNullableMapper();
    return new ApplicationMapperImpl(
        new ClientDetailsMapperImpl(new AddressMapperImpl(nullableMapper), nullableMapper),
        new DeclarationMapperImpl(),
        new EligibilityMapperImpl(nullableMapper),
        new EvidenceMapperImpl(),
        new ScopingQuestionsMapperImpl(),
        nullableMapper);
  }
}
