package com.hedera.node.app.itest;

import com.hedera.node.app.ServiceFacade;
import com.hedera.node.app.spi.Service;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ServiceFacadeTest {

	@Test
	void testAllServices() {
		//when
		final Set<Service> services = ServiceFacade.getAll();

		//then
		assertNotNull(services, "Set must never be null");
		System.out.println(services.size());
	}
}
