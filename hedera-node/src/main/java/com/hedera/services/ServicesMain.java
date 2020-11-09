package com.hedera.services;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hedera.services.context.ServicesContext;
import com.hedera.services.context.properties.Profile;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hedera.services.state.forensics.IssListener;
import com.hedera.services.utils.JvmSystemExits;
import com.hedera.services.utils.SystemExits;
import com.swirlds.common.NodeId;
import com.swirlds.common.Platform;
import com.swirlds.common.PlatformStatus;
import com.swirlds.common.SwirldMain;
import com.swirlds.common.SwirldState;
import com.swirlds.platform.Browser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Locale;
import java.util.function.Supplier;

import static com.hedera.services.context.SingletonContextsManager.CONTEXTS;
import static com.hedera.services.context.properties.Profile.DEV;
import static com.hedera.services.context.properties.Profile.PROD;
import static com.swirlds.common.PlatformStatus.ACTIVE;
import static com.swirlds.common.PlatformStatus.MAINTENANCE;

/**
 * Drives the major state transitions for a Hedera Node via its {@link ServicesContext}.
 *
 * @author Michael Tinker
 */
public class ServicesMain implements SwirldMain {
	private static final String START_INIT_MSG_PATTERN = "Using context to initialize HederaNode#%d...";

	static final long SUGGESTED_POST_CREATION_PAUSE_MS = 0L;
	public static Logger log = LogManager.getLogger(ServicesMain.class);

	SystemExits systemExits = new JvmSystemExits();
	Supplier<Charset> defaultCharset = Charset::defaultCharset;
	ServicesContext ctx;

	/**
	 * Convenience launcher for dev env.
	 *
	 * @param args
	 * 		ignored
	 */
	public static void main(String... args) {
		Browser.main(null);
	}

	@Override
	public void init(Platform ignore, NodeId nodeId) {
		if (!StandardCharsets.UTF_8.equals(defaultCharset.get())) {
			log.error("Default charset is {}, not UTF-8! Exiting.", defaultCharset.get());
			systemExits.fail(1);
		}
		try {
			CommonUtils.getSha384Hash();
		} catch (NoSuchAlgorithmException nsae) {
			log.error(nsae);
			systemExits.fail(1);
		}
		try {
			Locale.setDefault(Locale.US);
			ctx = CONTEXTS.lookup(nodeId.getId());
			logInfoWithConsoleEcho(String.format(START_INIT_MSG_PATTERN, ctx.id().getId()));
			contextDrivenInit();
			log.info("init finished.");
		} catch (IllegalStateException ise) {
			log.error("Fatal precondition violated in HederaNode#{}!", ctx.id(), ise);
			systemExits.fail(1);
		}
	}

	@Override
	public ServicesState newState() {
		return new ServicesState();
	}

	@Override
	public void platformStatusChange(PlatformStatus status) {
		log.info("Now current platform status = {} in HederaNode#{}.", status, ctx.id());
		ctx.platformStatus().set(status);
		if (status == ACTIVE) {
			ctx.recordStream().setInFreeze(false);
		} else if (status == MAINTENANCE) {
			ctx.recordStream().setInFreeze(true);
			ctx.updateFeature();
		} else {
			log.info("Platform {} status set to : {}", ctx.id(), status);
		}
	}

	@Override
	public void newSignedState(SwirldState signedState, Instant when, long round) {
		if (ctx.platformStatus().get() == MAINTENANCE) {
			((ServicesState)signedState).printHashes();
		}
		if (ctx.globalDynamicProperties().shouldExportBalances() && ctx.balancesExporter().isTimeToExport(when)) {
			try {
				ctx.balancesExporter().toCsvFile((ServicesState) signedState, when);
			} catch (IllegalStateException ise) {
				log.error("HederaNode#{} has invalid total balance in signed state, exiting!", ctx.id(), ise);
				systemExits.fail(1);
			}
		}
	}

	@Override
	public void run() {
		/* No-op. */
	}

	@Override
	public void preEvent() {
		/* No-op. */
	}

	private void contextDrivenInit() {
		registerIssListener();
		log.info("Platform callbacks registered.");
		checkPropertySources();
		log.info("Property sources are available.");
		configurePlatform();
		log.info("Platform is configured.");
		migrateStateIfNeeded();
		log.info("Migrations complete.");
		startRecordStreamThread();
		log.info("Record stream started.");
		startNettyIfAppropriate();
		log.info("Netty started.");
		createSystemAccountsIfNeeded();
		log.info("System accounts rationalized.");
		validateLedgerState();
		log.info("Ledger state ok.");
		createSystemFilesIfNeeded();
		log.info("System files rationalized.");
		exportAccountsIfDesired();
		log.info("Accounts exported.");
		initializeStats();
		log.info("Stats initialized.");

		log.info("Completed initialization of {} #{}", ctx.nodeType(), ctx.id());
	}

	private void startRecordStreamThread() {
		ctx.recordStreamThread().start();
	}

	private void exportAccountsIfDesired() {
		try {
			String path = ctx.properties().getStringProperty("hedera.accountsExportPath");
			ctx.accountsExporter().toFile(ctx.accounts(), path);
		} catch (Exception e) {
			throw new IllegalStateException("Could not export accounts!", e);
		}
	}

	private void createSystemFilesIfNeeded() {
		try {
			ctx.systemFilesManager().createAddressBookIfMissing();
			ctx.systemFilesManager().createNodeDetailsIfMissing();
			ctx.systemFilesManager().loadExchangeRates();
			ctx.systemFilesManager().createUpdateZipFileIfMissing();
		} catch (Exception e) {
			throw new IllegalStateException("Could not create system files!", e);
		}
	}

	private void createSystemAccountsIfNeeded() {
		try {
			ctx.systemAccountsCreator().ensureSystemAccounts(ctx.backingAccounts(), ctx.addressBook());
			ctx.pause().forMs(SUGGESTED_POST_CREATION_PAUSE_MS);
		} catch (Exception e) {
			throw new IllegalStateException("Could not create system accounts!", e);
		}
	}

	private void startNettyIfAppropriate() {
		int port = ctx.nodeLocalProperties().port();
		final int PORT_MODULUS = 1000;
		int tlsPort = ctx.nodeLocalProperties().tlsPort();
		log.info("TLS is turned on by default on node {}", ctx.id());
		Profile activeProfile = ctx.nodeLocalProperties().activeProfile();
		log.info("Active profile: {}", activeProfile);
		if (activeProfile == DEV) {
			if (onlyDefaultNodeListens()) {
				if (thisNodeIsDefaultListener()) {
					ctx.grpc().start(port, tlsPort, this::logInfoWithConsoleEcho);
				}
			} else {
				int portOffset = thisNodeIsDefaultListener()
						? 0
						: ctx.addressBook().getAddress(ctx.id().getId()).getPortExternalIpv4() % PORT_MODULUS;
				ctx.grpc().start(port + portOffset, tlsPort + portOffset, this::logInfoWithConsoleEcho);
			}
		} else if (activeProfile == PROD) {
			ctx.grpc().start(port, tlsPort, this::logInfoWithConsoleEcho);
		} else {
			log.warn("No Netty config for profile {}, skipping gRPC startup!", activeProfile);
		}
	}

	private boolean onlyDefaultNodeListens() {
		return ctx.properties().getBooleanProperty("dev.onlyDefaultNodeListens");
	}

	private boolean thisNodeIsDefaultListener() {
		String myNodeAccount = ctx.addressBook().getAddress(ctx.id().getId()).getMemo();
		String blessedNodeAccount = ctx.properties().getStringProperty("dev.defaultListeningNodeAccount");
		return myNodeAccount.equals(blessedNodeAccount);
	}

	private void loadFeeSchedule() {
		ctx.fees().init();
	}

	private void initializeStats() {
		ctx.statsManager().initializeFor(ctx.platform());
	}

	private void checkPropertySources() {
		ctx.propertySources().assertSourcesArePresent();
	}

	private void configurePlatform() {
		ctx.platform().setSleepAfterSync(0L);
	}

	private void migrateStateIfNeeded() {
		ctx.stateMigrations().runAllFor(ctx);
	}

	private void validateLedgerState() {
		ctx.ledgerValidator().assertIdsAreValid(ctx.accounts());
		if (!ctx.ledgerValidator().hasExpectedTotalBalance(ctx.accounts())) {
			log.error("Unexpected total balance in ledger, nodeId={}!", ctx.id());
			throw new IllegalStateException("Invalid total tinyBar float!");
		}
		if (ctx.nodeAccount() == null) {
			throw new IllegalStateException("Unknown ledger account!");
		}
	}

	void logInfoWithConsoleEcho(String s) {
		log.info(s);
		if (ctx.consoleOut() != null) {
			ctx.consoleOut().println(s);
		}
	}

	void registerIssListener() {
		ctx.platform().addSignedStateListener(new IssListener(ctx.issEventInfo()));
	}
}
