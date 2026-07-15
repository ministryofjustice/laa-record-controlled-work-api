package uk.gov.justice.laa.rcw.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.rcw.SpringBootMicroserviceApplication;
import uk.gov.justice.laa.rcw.generator.CreateApplicationRequestGenerator;
import uk.gov.justice.laa.rcw.model.CreateApplicationRequestBody;
import uk.gov.justice.laa.rcw.utils.BaseIntegrationTest;

@SpringBootTest(classes = SpringBootMicroserviceApplication.class)
@AutoConfigureMockMvc
@Transactional
class ApplicationsControllerIntegrationTest extends BaseIntegrationTest {

  @Test
  void shouldGetAllApplications() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/applications")
                .param("page", "1")
                .param("size", "1")
                .param("officeId", "a1b2c3d4-e5f6-7890-abcd-ef1234567890")
                .param("status", "DRAFT"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.*", hasSize(2)));
  }

  @Test
  void shouldGetApplication() throws Exception {
    mockMvc
        .perform(get("/api/v1/applications/a1b2c3d4-e5f6-7890-abcd-ef1234567890"))
        .andExpect(status().isOk())
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(
            jsonPath("$.individualLegalAidNumber").value("ebd50ba0-9ed9-4003-83a8-c11ac07d9e32"));
  }

  @Test
  void shouldCreateApplication() throws Exception {

    CreateApplicationRequestBody request =
        CreateApplicationRequestGenerator.createWithoutName(null);
    mockMvc
        .perform(
            post("/api/v1/applications")
                .contentType(MediaType.APPLICATION_JSON)
                .content(toJson(request))
                .accept(MediaType.APPLICATION_JSON))
        .andExpect(status().isCreated());
  }
}
