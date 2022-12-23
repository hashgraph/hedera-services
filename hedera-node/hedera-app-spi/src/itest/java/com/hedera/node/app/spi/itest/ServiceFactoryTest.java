package com.hedera.node.app.spi.itest;

import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.ServiceFactory;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ServiceFactoryTest {

	@Test
	public void testZeroResults() {
		//given
		final Set<Service> services = ServiceFactory.loadServices();

		//then
		assertNotNull(services, "Result must never be null");
		assertEquals(0, services.size(), "No services must be found");
	}
}
