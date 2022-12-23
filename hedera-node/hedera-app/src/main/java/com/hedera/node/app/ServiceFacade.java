package com.hedera.node.app;

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
import com.hedera.node.app.spi.ServiceFactory;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.Set;

public class ServiceFacade {

	@NonNull
	public static Set<Service> getAll() {
		return ServiceFactory.loadServices();
	}

	@NonNull
	public static FreezeService getFreezeService() {
		final FreezeService service = FreezeService.getInstance();
		if (service == null) {
			throw new IllegalStateException("Instance of " + FreezeService.class.getName() + " can not be found");
		}
		return service;
	}

	@NonNull
	public static ConsensusService getConsensusService() {
		final ConsensusService service = ConsensusService.getInstance();
		if (service == null) {
			throw new IllegalStateException("Instance of " + ConsensusService.class.getName() + " can not be found");
		}
		return service;
	}

	@NonNull
	public static FileService getFileService() {
		final FileService service = FileService.getInstance();
		if (service == null) {
			throw new IllegalStateException("Instance of " + FileService.class.getName() + " can not be found");
		}
		return service;
	}

	@NonNull
	public static NetworkService getNetworkService() {
		final NetworkService service = NetworkService.getInstance();
		if (service == null) {
			throw new IllegalStateException("Instance of " + NetworkService.class.getName() + " can not be found");
		}
		return service;
	}

	@NonNull
	public static ScheduleService getScheduleService() {
		final ScheduleService service = ScheduleService.getInstance();
		if (service == null) {
			throw new IllegalStateException("Instance of " + ScheduleService.class.getName() + " can not be found");
		}
		return service;
	}

	@NonNull
	public static ContractService getContractService() {
		final ContractService service = ContractService.getInstance();
		if (service == null) {
			throw new IllegalStateException("Instance of " + ContractService.class.getName() + " can not be found");
		}
		return service;
	}

	@NonNull
	public static CryptoService getCryptoService() {
		final CryptoService service = CryptoService.getInstance();
		if (service == null) {
			throw new IllegalStateException("Instance of " + CryptoService.class.getName() + " can not be found");
		}
		return service;
	}

	@NonNull
	public static TokenService getTokenService() {
		final TokenService service = TokenService.getInstance();
		if (service == null) {
			throw new IllegalStateException("Instance of " + TokenService.class.getName() + " can not be found");
		}
		return service;
	}

	@NonNull
	public static UtilService getUtilService() {
		final UtilService service = UtilService.getInstance();
		if (service == null) {
			throw new IllegalStateException("Instance of " + UtilService.class.getName() + " can not be found");
		}
		return service;
	}

}
