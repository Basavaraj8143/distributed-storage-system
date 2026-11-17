package com.byteharvest.master;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// The master refuses to start without a password, so the test supplies one.
@SpringBootTest(properties = "app.auth.password=test-password")
class MasterServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
