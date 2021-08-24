package com.hedera.services.grpc;

import com.hedera.services.context.properties.NodeLocalProperties;
import com.swirlds.common.Address;
import com.swirlds.common.NodeId;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.io.PrintStream;
import java.util.Optional;

@Singleton
public class GrpcStarter {
	private static final Logger log = LogManager.getLogger(GrpcStarter.class);

	private static final int PORT_MODULUS = 1000;

	private final NodeId nodeId;
	private final Address nodeAddress;
	private final GrpcServerManager grpc;
	private final NodeLocalProperties nodeLocalProperties;
	private final Optional<PrintStream> console;

	@Inject
	public GrpcStarter(
			NodeId nodeId,
			Address nodeAddress,
			GrpcServerManager grpc,
			NodeLocalProperties nodeLocalProperties,
			Optional<PrintStream> console
	) {
		this.nodeId = nodeId;
		this.console = console;
		this.nodeAddress = nodeAddress;
		this.grpc = grpc;
		this.nodeLocalProperties = nodeLocalProperties;
	}

	public void startIfAppropriate() {
		final var port = nodeLocalProperties.port();
		final var tlsPort = nodeLocalProperties.tlsPort();
		final var activeProfile = nodeLocalProperties.activeProfile();

		log.info("TLS is turned on by default on node {}", nodeId);
		log.info("Active profile: {}", activeProfile);

		switch (activeProfile) {
			case DEV:
				if (nodeLocalProperties.devOnlyDefaultNodeListens()) {
					if (thisNodeIsDefaultListener()) {
						grpc.start(port, tlsPort, this::logInfoWithConsoleEcho);
					}
				} else {
					int portOffset = thisNodeIsDefaultListener() ? 0 : nodeAddress.getPortExternalIpv4() % PORT_MODULUS;
					grpc.start(port + portOffset, tlsPort + portOffset, this::logInfoWithConsoleEcho);
				}
				break;
			case TEST:
				log.warn("No Netty config for profile {}, skipping gRPC startup", activeProfile);
				break;
			case PROD:
				grpc.start(port, tlsPort, this::logInfoWithConsoleEcho);
				break;
		}
	}

	private void logInfoWithConsoleEcho(String s) {
		log.info(s);
		console.ifPresent(c -> c.println(s));
	}

	private boolean thisNodeIsDefaultListener() {
		final var thisAccount = nodeAddress.getMemo();
		final var blessedNodeAccount = nodeLocalProperties.devListeningAccount();
		return blessedNodeAccount.equals(thisAccount);
	}
}
