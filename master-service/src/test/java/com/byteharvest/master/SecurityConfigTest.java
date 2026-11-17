package com.byteharvest.master;

import com.byteharvest.master.config.AuthProperties;
import com.byteharvest.master.config.SecurityConfig;
import com.byteharvest.master.controller.FileController;
import com.byteharvest.master.controller.HeartbeatController;
import com.byteharvest.master.service.DownloadService;
import com.byteharvest.master.service.FileManagementService;
import com.byteharvest.master.service.HeartbeatService;
import com.byteharvest.master.service.UploadService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Phase 1 rules: a login is needed everywhere except {@code POST /heartbeat}. */
@WebMvcTest({FileController.class, HeartbeatController.class})
@Import(SecurityConfig.class)
@TestPropertySource(properties = {"app.auth.username=admin", "app.auth.password=s3cret"})
class SecurityConfigTest {

    private static final String FILE_ID = "123e4567-e89b-12d3-a456-426614174000";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UploadService uploadService;

    @MockitoBean
    private DownloadService downloadService;

    @MockitoBean
    private FileManagementService fileManagementService;

    @MockitoBean
    private HeartbeatService heartbeatService;

    @Test
    void requestWithoutCredentialsIsUnauthorized() throws Exception {
        mockMvc.perform(get("/files"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongPasswordIsUnauthorized() throws Exception {
        mockMvc.perform(get("/files").with(httpBasic("admin", "wrong")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void wrongUsernameIsUnauthorized() throws Exception {
        mockMvc.perform(get("/files").with(httpBasic("someone-else", "s3cret")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void correctCredentialsAreAccepted() throws Exception {
        mockMvc.perform(get("/files").with(httpBasic("admin", "s3cret")))
                .andExpect(status().isOk());
    }

    @Test
    void unauthorizedResponseDoesNotTriggerTheBrowserLoginDialog() throws Exception {
        mockMvc.perform(get("/files"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @Test
    void changingRequestsAlsoNeedCredentials() throws Exception {
        // 401 (not 403) proves CSRF is not what stops this request: the login is.
        mockMvc.perform(delete("/files/" + FILE_ID))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/upload"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void heartbeatStaysOpenUntilPhaseTwo() throws Exception {
        // An empty body is a 400 from the controller, which shows the request got past security.
        mockMvc.perform(post("/heartbeat").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void masterRefusesToStartWithoutAPassword() {
        assertThrows(IllegalArgumentException.class, () -> new AuthProperties("admin", ""));
        assertThrows(IllegalArgumentException.class, () -> new AuthProperties("admin", null));
        assertThrows(IllegalArgumentException.class, () -> new AuthProperties(" ", "s3cret"));
    }
}
