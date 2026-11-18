package com.byteharvest.storage_node;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// The node refuses to start without a node token, so the test supplies one.
@SpringBootTest(properties = "app.node.token=test-node-token-0123456789")
class StorageNodeApplicationTests {

	@Test
	void contextLoads() {
	}

}
