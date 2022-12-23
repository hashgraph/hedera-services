package com.hedera.node.app.itest;

import com.hedera.node.app.ServiceFacade;
import com.hedera.node.app.service.admin.FreezeService;
import com.hedera.node.app.service.consensus.ConsensusService;
import com.hedera.node.app.service.contract.ContractService;
import com.hedera.node.app.service.file.FileService;
import com.hedera.node.app.service.network.NetworkService;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.service.token.CryptoService;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.util.UtilService;
import com.hedera.node.app.spi.Service;
import org.junit.jupiter.api.Test;

import java.util.Objects;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ServiceFacadeTest {

	@Test
	void testAllServices() {
		//when
		final Set<Service> services = ServiceFacade.getAll();

		//then
		assertNotNull(services, "Set must never be null");
		assertEquals(9, services.size(), "All 9 services must be part of the set");

		assertEquals(1, countForServiceType(services, FreezeService.class),
				"Must contain exactly 1 implementation of the service type");
		assertEquals(1, countForServiceType(services, ConsensusService.class),
				"Must contain exactly 1 implementation of the service type");
		assertEquals(1, countForServiceType(services, FileService.class),
				"Must contain exactly 1 implementation of the service type");
		assertEquals(1, countForServiceType(services, NetworkService.class),
				"Must contain exactly 1 implementation of the service type");
		assertEquals(1, countForServiceType(services, ScheduleService.class),
				"Must contain exactly 1 implementation of the service type");
		assertEquals(1, countForServiceType(services, ContractService.class),
				"Must contain exactly 1 implementation of the service type");
		assertEquals(1, countForServiceType(services, CryptoService.class),
				"Must contain exactly 1 implementation of the service type");
		assertEquals(1, countForServiceType(services, TokenService.class),
				"Must contain exactly 1 implementation of the service type");
		assertEquals(1, countForServiceType(services, UtilService.class),
				"Must contain exactly 1 implementation of the service type");
	}

	@Test
	void testServiceTypes() {
		assertNotNull(ServiceFacade.getContractService(), "Must provide an instance for the service");
		assertNotNull(ServiceFacade.getCryptoService(), "Must provide an instance for the service");
		assertNotNull(ServiceFacade.getConsensusService(), "Must provide an instance for the service");
		assertNotNull(ServiceFacade.getFileService(), "Must provide an instance for the service");
		assertNotNull(ServiceFacade.getFreezeService(), "Must provide an instance for the service");
		assertNotNull(ServiceFacade.getNetworkService(), "Must provide an instance for the service");
		assertNotNull(ServiceFacade.getScheduleService(), "Must provide an instance for the service");
		assertNotNull(ServiceFacade.getUtilService(), "Must provide an instance for the service");
		assertNotNull(ServiceFacade.getTokenService(), "Must provide an instance for the service");
	}

	private <S extends Service> long countForServiceType(final Set<Service> services, final Class<S> serviceType) {
		Objects.requireNonNull(services);
		Objects.requireNonNull(serviceType);
		return services.stream()
				.filter(s -> serviceType.isAssignableFrom(s.getClass()))
				.count();
	}
}
