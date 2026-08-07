package uk.gov.justice.laa.rcw.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.rcw.generator.ApplicationGenerator;
import uk.gov.justice.laa.rcw.model.Application;

class StubApplicationFactoryTest {

  @Test
  void shouldBuildStubApplication_withGivenId() {
    UUID applicationId = UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901");
    Application expected = ApplicationGenerator.create(b -> b.id(applicationId));

    Application result = StubApplicationFactory.stubApplication(applicationId);

    assertThat(result)
        .usingRecursiveComparison()
        .ignoringFields(
            "individualLegalAidNumber",
            "providerFirmCode",
            "ecfFlag",
            "applicationType",
            "providerOfficeCode",
            "createdAt",
            "modifiedAt",
            "clientDetails.id",
            "clientDetails.createdAt",
            "clientDetails.modifiedAt",
            "clientDetails.address.id",
            "clientDetails.address.createdAt",
            "clientDetails.address.modifiedAt",
            "declaration.id",
            "declaration.createdAt",
            "declaration.modifiedAt",
            "evidence.id",
            "evidence.createdAt",
            "evidence.modifiedAt")
        .isEqualTo(expected);
  }
}
