package com.byteharvest.master;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** The API documentation is public, the API itself is not. */
@SpringBootTest(properties = {
        "app.auth.password=test-password",
        "app.node.token=test-node-token-0123456789"})
@AutoConfigureMockMvc
class OpenApiDocsTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void specIsReachableWithoutLoginAndListsTheEndpoints() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("ByteHarvest Master API"))
                .andExpect(jsonPath("$.paths['/upload']").exists())
                .andExpect(jsonPath("$.paths['/files']").exists())
                .andExpect(jsonPath("$.paths['/nodes/status']").exists());
    }

    @Test
    void heartbeatIsDocumentedWithTheNodeTokenNotTheUserLogin() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(jsonPath("$.paths['/heartbeat'].post.security[0].nodeToken").exists());
    }

    @Test
    void swaggerPageIsNotBehindTheLogin() throws Exception {
        int httpStatus = mockMvc.perform(get("/swagger-ui.html")).andReturn().getResponse().getStatus();
        assertNotEquals(401, httpStatus);
    }

    @Test
    void theApiItselfStillNeedsTheLogin() throws Exception {
        mockMvc.perform(get("/files")).andExpect(status().isUnauthorized());
    }
}
