package com.byteharvest.master;

import com.byteharvest.master.config.AppConfig;
import com.byteharvest.master.config.NodeAuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** The node token setting, and the master's RestTemplate that sends it to the nodes. */
class NodeTokenTest {

    private static final String TOKEN = "test-node-token-0123456789";

    @Test
    void masterRestTemplateSendsTheTokenOnEveryCallToANode() {
        RestTemplate restTemplate = new AppConfig().restTemplate(new NodeAuthProperties(TOKEN));
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://storage-node-1:5001/chunks"))
                .andExpect(header("X-Node-Token", TOKEN))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

        String body = restTemplate.getForObject("http://storage-node-1:5001/chunks", String.class);

        assertEquals("[]", body);
        server.verify();
    }

    @Test
    void acceptsATokenOfEnoughLength() {
        assertEquals(TOKEN, new NodeAuthProperties(TOKEN).token());
    }

    @Test
    void refusesToStartWithoutAToken() {
        IllegalArgumentException blank = assertThrows(IllegalArgumentException.class, () -> new NodeAuthProperties(""));
        assertTrue(blank.getMessage().contains("NODE_TOKEN"));
        assertThrows(IllegalArgumentException.class, () -> new NodeAuthProperties(null));
        assertThrows(IllegalArgumentException.class, () -> new NodeAuthProperties("   "));
    }

    @Test
    void refusesATokenThatIsTooShort() {
        assertThrows(IllegalArgumentException.class,
                () -> new NodeAuthProperties("x".repeat(NodeAuthProperties.MIN_LENGTH - 1)));
    }
}
