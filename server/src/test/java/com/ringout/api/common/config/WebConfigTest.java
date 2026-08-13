package com.ringout.api.common.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ringout.api.terms.dto.response.TermsAgreeResponse;
import com.ringout.api.terms.service.TermsService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest
@AutoConfigureMockMvc(addFilters = false)
class WebConfigTest {

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private TermsService termsService;

  @Test
  void v1_경로로_요청하면_정상_라우된다() throws Exception {
    // given
    given(termsService.termsAgree(any(), any()))
        .willReturn(new TermsAgreeResponse(List.of("SERVICE", "PRIVACY"), LocalDate.now()));

    // when // then
    mockMvc.perform(post("/api/v1/terms")
            .header("Authorization", "Bearer 1")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {
                  "termsTypes": ["SERVICE", "PRIVACY"],
                  "agreedAt": "2026-08-10"
                }
                """))
        .andExpect(status().isCreated());
  }
}