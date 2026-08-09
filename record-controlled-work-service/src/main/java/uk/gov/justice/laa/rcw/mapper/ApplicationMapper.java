package uk.gov.justice.laa.rcw.mapper;

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationResponse;
import uk.gov.justice.laa.ia.datastore.client.model.ApplicationSummary;
import uk.gov.justice.laa.ia.datastore.client.model.CreateAddressCommand;
import uk.gov.justice.laa.ia.datastore.client.model.CreateClientCommand;
import uk.gov.justice.laa.ia.datastore.client.model.StartApplicationCommand;
import uk.gov.justice.laa.rcw.model.Address;
import uk.gov.justice.laa.rcw.model.Application;
import uk.gov.justice.laa.rcw.model.ApplicationOverview;
import uk.gov.justice.laa.rcw.model.ApplicationState;
import uk.gov.justice.laa.rcw.model.ClientDetails;
import uk.gov.justice.laa.rcw.model.CreateApplicationRequestBody;

/** The mapper between the datastore's ApplicationSummary and the RCW ApplicationOverview. */
@Mapper(componentModel = "spring")
public interface ApplicationMapper {

  /**
   * Maps the given application summary to an application overview.
   *
   * @param applicationSummary the application summary
   * @return the application overview
   */
  @Mapping(target = "applicationRefNumber", source = "referenceNumber")
  @Mapping(target = "name", expression = "java(toName(applicationSummary))")
  ApplicationOverview toApplicationOverview(ApplicationSummary applicationSummary);

  /** Joins the client's first and last name, skipping any that are null. */
  default String toName(ApplicationSummary applicationSummary) {
    return Stream.of(
            applicationSummary.getClientFirstName(), applicationSummary.getClientLastName())
        .filter(Objects::nonNull)
        .collect(Collectors.joining(" "));
  }

  /** Maps the RCW application status to the datastore's equivalent enum. */
  default uk.gov.justice.laa.ia.datastore.client.model.ApplicationState toDatastoreApplicationState(
      ApplicationState status) {
    if (status == null) {
      return null;
    }
    return switch (status) {
      case DRAFT -> uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.DRAFT;
      case COMPLETED -> uk.gov.justice.laa.ia.datastore.client.model.ApplicationState.COMPLETED;
    };
  }

  /** Maps the RCW create request to the datastore start-application command. */
  default StartApplicationCommand toStartApplicationCommand(
      CreateApplicationRequestBody createApplicationRequestBody) {
    return StartApplicationCommand.builder()
        .client(toCreateClientCommand(createApplicationRequestBody.getClientDetails()))
        .applicationType(StartApplicationCommand.ApplicationTypeEnum.RCW)
        .providerOfficeCode(createApplicationRequestBody.getProviderOfficeCode())
        .build();
  }

  /** Maps the RCW client details to the datastore create client command. */
  default CreateClientCommand toCreateClientCommand(ClientDetails clientDetails) {
    if (clientDetails == null) {
      return null;
    }

    return CreateClientCommand.builder()
        .firstName(clientDetails.getFirstName())
        .lastName(clientDetails.getLastName())
        .dateOfBirth(clientDetails.getDateOfBirth())
        .nationalInsuranceNumber(clientDetails.getNiNumber())
        .noFixedAbode(!Boolean.TRUE.equals(clientDetails.getHasFixedAddress()))
        .createAddressCommand(toCreateAddressCommand(clientDetails.getAddress()))
        .build();
  }

  /** Maps the RCW address to the datastore create address command. */
  default CreateAddressCommand toCreateAddressCommand(Address address) {
    if (address == null) {
      return null;
    }

    return CreateAddressCommand.builder()
        .addressLine1(address.getAddressLine1())
        .addressLine2(address.getAddressLine2())
        .addressLine3(address.getAddressLine3())
        .addressLine4(address.getAddressLine4())
        .townOrCity(address.getTownOrCity())
        .postCode(address.getPostCode())
        .county(address.getCounty())
        .country(address.getCountry())
        .build();
  }

  /** Maps the datastore application response to the RCW application model. */
  default Application toApplication(ApplicationResponse applicationResponse) {
    if (applicationResponse == null) {
      return null;
    }

    return Application.builder()
        .id(applicationResponse.getId())
        .individualLegalAidNumber(applicationResponse.getIndividualLegalAidNumber())
        .providerFirmCode(applicationResponse.getProviderFirmCode())
        .providerOfficeCode(applicationResponse.getProviderOfficeCode())
        .clientDetails(toClientDetails(applicationResponse.getClient()))
        .applicationState(toApplicationState(applicationResponse.getApplicationState()))
        .ecfFlag(applicationResponse.getEcfFlag())
        .applicationType(applicationResponse.getApplicationType())
        .createdAt(applicationResponse.getCreatedAt())
        .createdBy(applicationResponse.getCreatedBy())
        .modifiedAt(applicationResponse.getModifiedAt())
        .modifiedBy(applicationResponse.getModifiedBy())
        .build();
  }

  /** Maps datastore application state back to the RCW application state. */
  default ApplicationState toApplicationState(
      uk.gov.justice.laa.ia.datastore.client.model.ApplicationState status) {
    if (status == null) {
      return null;
    }
    return switch (status) {
      case DRAFT -> ApplicationState.DRAFT;
      case COMPLETED -> ApplicationState.COMPLETED;
    };
  }

  /** Maps the datastore client details to the RCW client details model. */
  default ClientDetails toClientDetails(
      uk.gov.justice.laa.ia.datastore.client.model.ClientDetails clientDetails) {
    if (clientDetails == null) {
      return null;
    }

    return ClientDetails.builder()
        .id(clientDetails.getIndividualLegalAidNumber())
        .firstName(clientDetails.getFirstName())
        .lastName(clientDetails.getLastName())
        .dateOfBirth(clientDetails.getDateOfBirth())
        .niNumber(clientDetails.getNiNumber())
        .hasFixedAddress(!Boolean.TRUE.equals(clientDetails.getNoFixedAbode()))
        .address(toAddress(clientDetails.getAddress()))
        .createdAt(clientDetails.getCreatedAt())
        .modifiedAt(clientDetails.getModifiedAt())
        .build();
  }

  /** Maps the datastore address to the RCW address model. */
  default Address toAddress(uk.gov.justice.laa.ia.datastore.client.model.Address address) {
    if (address == null) {
      return null;
    }

    return Address.builder()
        .addressLine1(address.getAddressLine1())
        .addressLine2(address.getAddressLine2())
        .addressLine3(address.getAddressLine3())
        .addressLine4(address.getAddressLine4())
        .townOrCity(address.getTownOrCity())
        .postCode(address.getPostCode())
        .county(address.getCounty())
        .country(address.getCountry())
        .createdAt(address.getCreatedAt())
        .modifiedAt(address.getModifiedAt())
        .build();
  }
}
