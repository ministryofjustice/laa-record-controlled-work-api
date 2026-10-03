package uk.gov.justice.laa.rcw.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.openapitools.jackson.nullable.JsonNullable;
import uk.gov.justice.laa.ia.datastore.client.model.EligibilityResult;
import uk.gov.justice.laa.rcw.model.EligibilityIndication;

class EligibilityMapperTest {

  private final JsonNullableMapper jsonNullableMapper = new JsonNullableMapperImpl();
  private final EligibilityMapper eligibilityMapper = new EligibilityMapperImpl(jsonNullableMapper);

  @Test
  void shouldWrapGenericValuesAsPresentIncludingNull() {
    assertThat(jsonNullableMapper.toJsonNullable("value")).isEqualTo(JsonNullable.of("value"));
    assertThat(jsonNullableMapper.toJsonNullable(null)).isEqualTo(JsonNullable.of(null));
  }

  @Test
  void shouldMapEligibilityResult() {
    var data =
        uk.gov.justice.laa.ia.datastore.client.model.EligibilityData.builder()
            .levelOfHelp("controlled")
            .adultDependants(false)
            .adultDependantsCount(0)
            .build();
    EligibilityResult eligibilityResult =
        EligibilityResult.builder().data(data).result(Map.of("eligible", true)).build();

    var result = eligibilityMapper.toEligibility(eligibilityResult);

    assertThat(result.getData().getLevelOfHelp()).isEqualTo("controlled");
    assertThat(result.getData().getAdultDependants()).isFalse();
    assertThat(result.getData().getAdultDependantsCount()).isZero();
    assertThat(result.getResult()).isEqualTo(Map.of("eligible", true));
  }

  @Test
  void shouldMapNullEligibilityResultToNull() {
    assertThat(eligibilityMapper.toEligibility(null)).isNull();
  }

  @Test
  void shouldMapEligibilityIndicationInBothDirections() {
    assertThat(eligibilityMapper.toDatastoreEligibilityIndication(EligibilityIndication.ELIGIBLE))
        .isEqualTo(uk.gov.justice.laa.ia.datastore.client.model.EligibilityIndication.ELIGIBLE);
    assertThat(eligibilityMapper.toDatastoreEligibilityIndication(EligibilityIndication.INELIGIBLE))
        .isEqualTo(uk.gov.justice.laa.ia.datastore.client.model.EligibilityIndication.INELIGIBLE);
    assertThat(
            eligibilityMapper.toEligibilityIndication(
                uk.gov.justice.laa.ia.datastore.client.model.EligibilityIndication.ELIGIBLE))
        .isEqualTo(EligibilityIndication.ELIGIBLE);
    assertThat(
            eligibilityMapper.toEligibilityIndication(
                uk.gov.justice.laa.ia.datastore.client.model.EligibilityIndication.INELIGIBLE))
        .isEqualTo(EligibilityIndication.INELIGIBLE);
    assertThat(eligibilityMapper.toDatastoreEligibilityIndication(null)).isNull();
    assertThat(eligibilityMapper.toEligibilityIndication(null)).isNull();
  }

  @Test
  void shouldMapEligibilityDataToDatastoreMeansData() {
    var data =
        uk.gov.justice.laa.rcw.model.EligibilityData.builder()
            .levelOfHelp("controlled")
            .adultDependants(true)
            .incomes(
                List.of(
                    uk.gov.justice.laa.rcw.model.EligibilityDataIncome.builder()
                        .grossIncome(BigDecimal.valueOf(500))
                        .incomeFrequency("monthly")
                        .build()))
            .bankAccounts(
                List.of(
                    uk.gov.justice.laa.rcw.model.EligibilityDataBankAccount.builder()
                        .amount(BigDecimal.valueOf(1000))
                        .build()))
            .build();

    var result = eligibilityMapper.toDatastoreMeansData(data);

    assertThat(result.getLevelOfHelp_JsonNullable()).isEqualTo(JsonNullable.of("controlled"));
    assertThat(result.getAdultDependants_JsonNullable()).isEqualTo(JsonNullable.of(true));
    assertThat(result.getIncomes().get(0).getGrossIncome_JsonNullable())
        .isEqualTo(JsonNullable.of(BigDecimal.valueOf(500)));
    assertThat(result.getIncomes().get(0).getIncomeFrequency_JsonNullable())
        .isEqualTo(JsonNullable.of("monthly"));
    assertThat(result.getBankAccounts().get(0).getAmount_JsonNullable())
        .isEqualTo(JsonNullable.of(BigDecimal.valueOf(1000)));
    assertThat(result.getApiResponse_JsonNullable()).isEqualTo(JsonNullable.undefined());
  }

  @Test
  void shouldMapNullEligibilityDataToNull() {
    assertThat(eligibilityMapper.toDatastoreMeansData(null)).isNull();
  }

  @Test
  void shouldPreserveAbsentNullableListsAsUndefined() {
    var result =
        eligibilityMapper.toDatastoreMeansData(
            uk.gov.justice.laa.rcw.model.EligibilityData.builder().build());

    assertThat(result.getAdditionalProperties_JsonNullable()).isEqualTo(JsonNullable.undefined());
    assertThat(result.getBankAccounts_JsonNullable()).isEqualTo(JsonNullable.undefined());
    assertThat(result.getBenefits_JsonNullable()).isEqualTo(JsonNullable.undefined());
    assertThat(result.getDependantIncomes_JsonNullable()).isEqualTo(JsonNullable.undefined());
    assertThat(result.getIncomes_JsonNullable()).isEqualTo(JsonNullable.undefined());
    assertThat(result.getPartnerAdditionalProperties_JsonNullable())
        .isEqualTo(JsonNullable.undefined());
    assertThat(result.getPartnerBankAccounts_JsonNullable()).isEqualTo(JsonNullable.undefined());
    assertThat(result.getPartnerBenefits_JsonNullable()).isEqualTo(JsonNullable.undefined());
    assertThat(result.getPartnerIncomes_JsonNullable()).isEqualTo(JsonNullable.undefined());
    assertThat(result.getVehicles_JsonNullable()).isEqualTo(JsonNullable.undefined());
  }

  @Test
  void shouldPreserveEmptyNullableListsAsPresent() {
    var result =
        eligibilityMapper.toDatastoreMeansData(
            uk.gov.justice.laa.rcw.model.EligibilityData.builder()
                .additionalProperties(List.of())
                .bankAccounts(List.of())
                .benefits(List.of())
                .dependantIncomes(List.of())
                .incomes(List.of())
                .partnerAdditionalProperties(List.of())
                .partnerBankAccounts(List.of())
                .partnerBenefits(List.of())
                .partnerIncomes(List.of())
                .vehicles(List.of())
                .build());

    assertThat(result.getAdditionalProperties_JsonNullable()).isEqualTo(JsonNullable.of(List.of()));
    assertThat(result.getBankAccounts_JsonNullable()).isEqualTo(JsonNullable.of(List.of()));
    assertThat(result.getBenefits_JsonNullable()).isEqualTo(JsonNullable.of(List.of()));
    assertThat(result.getDependantIncomes_JsonNullable()).isEqualTo(JsonNullable.of(List.of()));
    assertThat(result.getIncomes_JsonNullable()).isEqualTo(JsonNullable.of(List.of()));
    assertThat(result.getPartnerAdditionalProperties_JsonNullable())
        .isEqualTo(JsonNullable.of(List.of()));
    assertThat(result.getPartnerBankAccounts_JsonNullable()).isEqualTo(JsonNullable.of(List.of()));
    assertThat(result.getPartnerBenefits_JsonNullable()).isEqualTo(JsonNullable.of(List.of()));
    assertThat(result.getPartnerIncomes_JsonNullable()).isEqualTo(JsonNullable.of(List.of()));
    assertThat(result.getVehicles_JsonNullable()).isEqualTo(JsonNullable.of(List.of()));
  }

  @Test
  void shouldMapEachPopulatedNullableList() {
    var data =
        uk.gov.justice.laa.rcw.model.EligibilityData.builder()
            .additionalProperties(
                List.of(
                    uk.gov.justice.laa.rcw.model.EligibilityDataProperty.builder()
                        .houseValue(BigDecimal.valueOf(25000))
                        .build()))
            .bankAccounts(
                List.of(
                    uk.gov.justice.laa.rcw.model.EligibilityDataBankAccount.builder()
                        .amount(BigDecimal.valueOf(1000))
                        .build()))
            .benefits(
                List.of(
                    uk.gov.justice.laa.rcw.model.EligibilityDataBenefit.builder()
                        .benefitType("housing")
                        .build()))
            .dependantIncomes(
                List.of(
                    uk.gov.justice.laa.rcw.model.EligibilityDataDependantIncome.builder()
                        .amount(BigDecimal.valueOf(250))
                        .build()))
            .incomes(
                List.of(
                    uk.gov.justice.laa.rcw.model.EligibilityDataIncome.builder()
                        .grossIncome(BigDecimal.valueOf(500))
                        .build()))
            .partnerAdditionalProperties(
                List.of(
                    uk.gov.justice.laa.rcw.model.EligibilityDataProperty.builder()
                        .houseValue(BigDecimal.valueOf(35000))
                        .build()))
            .partnerBankAccounts(
                List.of(
                    uk.gov.justice.laa.rcw.model.EligibilityDataBankAccount.builder()
                        .amount(BigDecimal.valueOf(900))
                        .build()))
            .partnerBenefits(
                List.of(
                    uk.gov.justice.laa.rcw.model.EligibilityDataBenefit.builder()
                        .benefitType("child")
                        .build()))
            .partnerIncomes(
                List.of(
                    uk.gov.justice.laa.rcw.model.EligibilityDataIncome.builder()
                        .grossIncome(BigDecimal.valueOf(600))
                        .build()))
            .vehicles(
                List.of(
                    uk.gov.justice.laa.rcw.model.EligibilityDataVehicle.builder()
                        .vehicleValue(BigDecimal.valueOf(5000))
                        .build()))
            .build();

    var result = eligibilityMapper.toDatastoreMeansData(data);

    assertThat(result.getAdditionalProperties_JsonNullable().get()).hasSize(1);
    assertThat(result.getAdditionalProperties().get(0).getHouseValue_JsonNullable())
        .isEqualTo(JsonNullable.of(BigDecimal.valueOf(25000)));
    assertThat(result.getBankAccounts_JsonNullable().get()).hasSize(1);
    assertThat(result.getBankAccounts().get(0).getAmount_JsonNullable())
        .isEqualTo(JsonNullable.of(BigDecimal.valueOf(1000)));
    assertThat(result.getBenefits_JsonNullable().get()).hasSize(1);
    assertThat(result.getBenefits().get(0).getBenefitType_JsonNullable())
        .isEqualTo(JsonNullable.of("housing"));
    assertThat(result.getDependantIncomes_JsonNullable().get()).hasSize(1);
    assertThat(result.getDependantIncomes().get(0).getAmount_JsonNullable())
        .isEqualTo(JsonNullable.of(BigDecimal.valueOf(250)));
    assertThat(result.getIncomes_JsonNullable().get()).hasSize(1);
    assertThat(result.getIncomes().get(0).getGrossIncome_JsonNullable())
        .isEqualTo(JsonNullable.of(BigDecimal.valueOf(500)));
    assertThat(result.getPartnerAdditionalProperties_JsonNullable().get()).hasSize(1);
    assertThat(result.getPartnerAdditionalProperties().get(0).getHouseValue_JsonNullable())
        .isEqualTo(JsonNullable.of(BigDecimal.valueOf(35000)));
    assertThat(result.getPartnerBankAccounts_JsonNullable().get()).hasSize(1);
    assertThat(result.getPartnerBankAccounts().get(0).getAmount_JsonNullable())
        .isEqualTo(JsonNullable.of(BigDecimal.valueOf(900)));
    assertThat(result.getPartnerBenefits_JsonNullable().get()).hasSize(1);
    assertThat(result.getPartnerBenefits().get(0).getBenefitType_JsonNullable())
        .isEqualTo(JsonNullable.of("child"));
    assertThat(result.getPartnerIncomes_JsonNullable().get()).hasSize(1);
    assertThat(result.getPartnerIncomes().get(0).getGrossIncome_JsonNullable())
        .isEqualTo(JsonNullable.of(BigDecimal.valueOf(600)));
    assertThat(result.getVehicles_JsonNullable().get()).hasSize(1);
    assertThat(result.getVehicles().get(0).getVehicleValue_JsonNullable())
        .isEqualTo(JsonNullable.of(BigDecimal.valueOf(5000)));
  }

  @Test
  void shouldMapAbsentAndPopulatedEarlyResult() {
    var absent =
        eligibilityMapper.toDatastoreMeansData(
            uk.gov.justice.laa.rcw.model.EligibilityData.builder().build());
    var populated =
        eligibilityMapper.toDatastoreMeansData(
            uk.gov.justice.laa.rcw.model.EligibilityData.builder()
                .earlyResult(
                    uk.gov.justice.laa.rcw.model.EligibilityDataEarlyResult.builder()
                        .result("pass")
                        .grossIncomeExcess(BigDecimal.valueOf(100))
                        .type("gross_income")
                        .build())
                .build());

    assertThat(absent.getEarlyResult_JsonNullable()).isEqualTo(JsonNullable.undefined());
    assertThat(populated.getEarlyResult_JsonNullable().get().getResult_JsonNullable())
        .isEqualTo(JsonNullable.of("pass"));
    assertThat(populated.getEarlyResult_JsonNullable().get().getGrossIncomeExcess_JsonNullable())
        .isEqualTo(JsonNullable.of(BigDecimal.valueOf(100)));
    assertThat(populated.getEarlyResult_JsonNullable().get().getType_JsonNullable())
        .isEqualTo(JsonNullable.of("gross_income"));
  }
}
