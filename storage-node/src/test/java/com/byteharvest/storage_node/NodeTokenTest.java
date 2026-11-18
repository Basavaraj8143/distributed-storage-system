package com.byteharvest.storage_node;

import com.byteharvest.storage_node.config.AppConfig;
import com.byteharvest.storage_node.config.NodeAuthProperties;
import com.byteharvest.storage_node.config.NodeTokenFilter;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** The node only serves callers that know the shared token, and sends it with its own heartbeats. */
class NodeTokenTest {

    private static final String TOKEN = "test-node-token-0123456789";

    private final NodeTokenFilter filter = new NodeTokenFilter(new NodeAuthProperties(TOKEN));

    private record Result(MockHttpServletResponse response, MockFilterChain chain) {
        boolean reachedController() {
            return chain.getRequest() != null;
        }
    }

    private Result call(String method, String path, String tokenHeader) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        if (tokenHeader != null) {
            request.addHeader("X-Node-Token", tokenHeader);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, response, chain);
        return new Result(response, chain);
    }

    @Test
    void requestWithoutTokenIsRejected() throws Exception {
        Result result = call("GET", "/chunks", null);

        assertEquals(401, result.response().getStatus());
        assertNull(result.chain().getRequest(), "the request must not reach the controller");
    }

    @Test
    void requestWithWrongTokenIsRejected() throws Exception {
        Result result = call("GET", "/getChunk/abc", "not-the-right-token-at-all");

        assertEquals(401, result.response().getStatus());
        assertEquals(false, result.reachedController());
    }

    @Test
    void requestWithTheTokenIsPassedOn() throws Exception {
        Result result = call("GET", "/chunks", TOKEN);

        assertEquals(200, result.response().getStatus());
        assertNotNull(result.chain().getRequest());
    }

    @Test
    void everyEndpointIsGuarded_includingStoreDeleteAndSimulate() throws Exception {
        assertEquals(401, call("POST", "/storeChunk", null).response().getStatus());
        assertEquals(401, call("DELETE", "/deleteChunk/abc", null).response().getStatus());
        assertEquals(401, call("POST", "/simulate/offline", null).response().getStatus());
    }

    @Test
    void heartbeatToTheMasterCarriesTheToken() {
        RestTemplate restTemplate = new AppConfig().restTemplate(new NodeAuthProperties(TOKEN));
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        server.expect(requestTo("http://master:8080/heartbeat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Node-Token", TOKEN))
                .andRespond(withSuccess("Heartbeat received", MediaType.TEXT_PLAIN));

        restTemplate.postForEntity("http://master:8080/heartbeat",
                Map.of("nodeId", "node-1", "nodeUrl", "http://storage-node-1:5001"), String.class);

        server.verify();
    }

    @Test
    void refusesToStartWithoutAUsableToken() {
        assertThrows(IllegalArgumentException.class, () -> new NodeAuthProperties(null));
        assertThrows(IllegalArgumentException.class, () -> new NodeAuthProperties(""));
        assertThrows(IllegalArgumentException.class,
                () -> new NodeAuthProperties("x".repeat(NodeAuthProperties.MIN_LENGTH - 1)));
    }
}
