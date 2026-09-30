package uk.gov.justice.laa.rcw.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.ia.datastore.client.model.DeclarationResponse;
import uk.gov.justice.laa.rcw.model.Declaration;

class DeclarationMapperTest {

  private final DeclarationMapper declarationMapper = new DeclarationMapperImpl();

  @Test
  void shouldMapDeclarationResponse() {
    OffsetDateTime timestamp = OffsetDateTime.parse("2024-01-01T09:00:00Z");
    UUID id = UUID.fromString("d4e5f6a7-b8c9-0123-def1-234567890123");
    DeclarationResponse declarationResponse =
        DeclarationResponse.builder()
            .id(id)
            .declarationConfirmation(true)
            .createdAt(timestamp)
            .createdBy("Test User")
            .modifiedAt(timestamp)
            .modifiedBy("Test User")
            .build();

    Declaration result = declarationMapper.toDeclaration(declarationResponse);

    assertThat(result.getId()).isEqualTo(id);
    assertThat(result.getDeclarationConfirmation()).isTrue();
    assertThat(result.getCreatedAt()).isEqualTo(timestamp);
    assertThat(result.getCreatedBy()).isEqualTo("Test User");
    assertThat(result.getModifiedAt()).isEqualTo(timestamp);
    assertThat(result.getModifiedBy()).isEqualTo("Test User");
  }

  @Test
  void shouldMapNullDeclarationResponseToNull() {
    assertThat(declarationMapper.toDeclaration(null)).isNull();
  }
}
