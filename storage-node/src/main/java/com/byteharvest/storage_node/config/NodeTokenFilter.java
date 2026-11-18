package com.byteharvest.storage_node.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Rejects every request to this node that does not carry the shared node token.
 * Only the master knows the token, so nobody else can store, read, list or delete chunks,
 * or switch the node's simulated offline mode.
 */
@Component
public class NodeTokenFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Node-Token";

    private final byte[] expected;

    public NodeTokenFilter(NodeAuthProperties properties) {
        this.expected = properties.token().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String given = request.getHeader(HEADER);
        // isEqual compares in constant time, so response timing does not reveal how much of a guess was right.
        boolean valid = given != null && MessageDigest.isEqual(expected, given.getBytes(StandardCharsets.UTF_8));
        if (!valid) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        chain.doFilter(request, response);
    }
}
