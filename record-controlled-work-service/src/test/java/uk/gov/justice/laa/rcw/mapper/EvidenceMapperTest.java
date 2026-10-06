package uk.gov.justice.laa.rcw.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.ia.datastore.client.model.EvidenceResponse;
import uk.gov.justice.laa.rcw.model.Evidence;

class EvidenceMapperTest {

  private final EvidenceMapper evidenceMapper = new EvidenceMapperImpl();

  @Test
  void shouldMapEvidenceResponse() {
    EvidenceResponse evidenceResponse =
        EvidenceResponse.builder()
            .evidenceExemptionCode("adviceOverPhone")
            .evidenceExemptionReason("Client was advised over the phone")
            .incomeEvidenceChecklist(Map.of("payslips", true))
            .expenditureCapitalEvidenceChecklist(Map.of("bankStatements", true))
            .build();

    Evidence result = evidenceMapper.toEvidence(evidenceResponse);

    assertThat(result.getEvidenceExemptionCode()).isEqualTo("adviceOverPhone");
    assertThat(result.getEvidenceExemptionReason()).isEqualTo("Client was advised over the phone");
    assertThat(result.getIncomeEvidenceChecklist()).isEqualTo(Map.of("payslips", true));
    assertThat(result.getExpenditureCapitalEvidenceChecklist())
        .isEqualTo(Map.of("bankStatements", true));
  }

  @Test
  void shouldMapNullEvidenceResponseToNull() {
    assertThat(evidenceMapper.toEvidence(null)).isNull();
  }
}
