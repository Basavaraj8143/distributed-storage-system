package com.byteharvest.master;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// The master refuses to start without a password and a node token, so the test supplies both.
@SpringBootTest(properties = {
        "app.auth.password=test-password",
        "app.node.token=test-node-token-0123456789"})
class MasterServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
