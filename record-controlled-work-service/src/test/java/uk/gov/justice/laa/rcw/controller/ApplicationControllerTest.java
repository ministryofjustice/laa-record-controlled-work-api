package uk.gov.justice.laa.rcw.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.justice.laa.rcw.generator.ApplicationGenerator;
import uk.gov.justice.laa.rcw.model.Application;
import uk.gov.justice.laa.rcw.service.ApplicationService;

@WebMvcTest(ApplicationController.class)
class ApplicationControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private ApplicationService mockApplicationService;

  @Test
  void getApplications_returnsOkStatusAndAllApplications() throws Exception {
    List<Application> applications =
        List.of(
            ApplicationGenerator.create(null),
            ApplicationGenerator.create(
                    b ->
                        b.id(UUID.fromString("b2c3d4e5-f6a7-8901-bcde-f12345678901"))
                            .name("Other Random Name")
                            .modifiedAt(OffsetDateTime.now()))
                .applicationRefNumber("CW-222222"));

    when(mockApplicationService.getApplications()).thenReturn(applications);

    mockMvc
        .perform(get("/api/v1/applications"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.*", hasSize(2)))
        .andExpect(jsonPath("$[0].id").value("a1b2c3d4-e5f6-7890-abcd-ef1234567890"))
        .andExpect(jsonPath("$[0].name").value("Random Name"))
        .andExpect(jsonPath("$[0].modifiedAt").exists())
        .andExpect(jsonPath("$[0].applicationRefNumber").value("CW-111111"))
        .andExpect(jsonPath("$[1].id").value("b2c3d4e5-f6a7-8901-bcde-f12345678901"))
        .andExpect(jsonPath("$[1].name").value("Other Random Name"))
        .andExpect(jsonPath("$[1].modifiedAt").exists())
        .andExpect(jsonPath("$[1].applicationRefNumber").value("CW-222222"));
  }

  @Test
  void getApplications_returnsEmptyListWhenNoApplications() throws Exception {
    when(mockApplicationService.getApplications()).thenReturn(List.of());

    mockMvc
        .perform(get("/api/v1/applications"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.*", hasSize(0)));
  }
}
