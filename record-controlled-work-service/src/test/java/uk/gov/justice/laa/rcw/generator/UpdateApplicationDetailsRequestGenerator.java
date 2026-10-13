package uk.gov.justice.laa.rcw.generator;

import java.time.LocalDate;
import uk.gov.justice.laa.rcw.model.CreateScopingQuestions;
import uk.gov.justice.laa.rcw.model.FamilyLawClassification;
import uk.gov.justice.laa.rcw.model.PriorLegalAid;
import uk.gov.justice.laa.rcw.model.UpdateApplicationDetailsRequestBody;
import uk.gov.justice.laa.rcw.model.UpdateClientDetailsRequestBody;

/** Creates valid editable application details requests for tests. */
public final class UpdateApplicationDetailsRequestGenerator {

  private UpdateApplicationDetailsRequestGenerator() {}

  /** Creates the standard valid request used by details integration tests. */
  public static UpdateApplicationDetailsRequestBody validRequest() {
    return new UpdateApplicationDetailsRequestBody()
        .scopingQuestions(
            new CreateScopingQuestions()
                .priorLegalAid(PriorLegalAid.NO)
                .familyLawClassification(FamilyLawClassification.PRIVATE)
                .needsAdviceOnEUOrInternationalMaintenance(false)
                .needsProtectiveInjunction(false))
        .legalAidLast6Months(false)
        .reasonForReapplication(null)
        .ecfFlag(true)
        .clientDetails(
            new UpdateClientDetailsRequestBody()
                .firstName("Test")
                .lastName("Client")
                .dateOfBirth(LocalDate.of(1990, 1, 1))
                .niNumber(null)
                .hasFixedAddress(false)
                .address(null));
  }
}
