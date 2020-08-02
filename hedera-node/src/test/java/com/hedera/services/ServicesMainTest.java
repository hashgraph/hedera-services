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

import com.hedera.services.context.CurrentPlatformStatus;
import com.hedera.services.context.ServicesContext;
import com.hedera.services.context.properties.Profile;
import com.hedera.services.context.properties.PropertySanitizer;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.context.properties.PropertySources;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.grpc.GrpcServerManager;
import com.hedera.services.ledger.accounts.BackingAccounts;
import com.hedera.services.legacy.exception.InvalidTotalAccountBalanceException;
import com.hedera.services.legacy.services.stats.HederaNodeStats;
import com.hedera.services.legacy.stream.RecordStream;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.exports.AccountsExporter;
import com.hedera.services.state.exports.BalancesExporter;
import com.hedera.services.state.forensics.IssListener;
import com.hedera.services.state.initialization.SystemAccountsCreator;
import com.hedera.services.state.initialization.SystemFilesManager;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.migration.StateMigrations;
import com.hedera.services.state.validation.LedgerValidator;
import com.hedera.services.utils.Pause;
import com.hedera.services.utils.SystemExits;
import com.hedera.services.utils.TimerUtils;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.common.Address;
import com.swirlds.common.AddressBook;
import com.swirlds.common.Console;
import com.swirlds.common.NodeId;
import com.swirlds.common.Platform;
import com.swirlds.common.PlatformStatus;
import com.swirlds.fcmap.FCMap;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.InOrder;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static com.hedera.services.context.SingletonContextsManager.CONTEXTS;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.inOrder;
import static org.mockito.BDDMockito.intThat;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;
import static org.mockito.BDDMockito.verifyNoInteractions;
import static org.mockito.BDDMockito.willThrow;

@RunWith(JUnitPlatform.class)
public class ServicesMainTest {
	final long NODE_ID = 1L;
	final String PATH = "/this/was/mr/bleaneys/room";

	FCMap topics;
	FCMap accounts;
	FCMap storage;
	Pause pause;
	Thread recordStreamThread;
	Logger mockLog;
	Console console;
	Platform platform;
	SystemExits systemExits;
	AddressBook addressBook;
	PrintStream consoleOut;
	RecordStream recordStream;
	FeeCalculator fees;
	ServicesMain subject;
	ServicesContext ctx;
	PropertySource properties;
	LedgerValidator ledgerValidator;
	AccountsExporter accountsExporter;
	PropertySources propertySources;
	BalancesExporter balancesExporter;
	PropertySanitizer propertySanitizer;
	StateMigrations stateMigrations;
	HederaNodeStats stats;
	GrpcServerManager grpc;
	SystemFilesManager systemFilesManager;
	SystemAccountsCreator systemAccountsCreator;
	CurrentPlatformStatus platformStatus;
	AccountRecordsHistorian recordsHistorian;
	BackingAccounts<AccountID, MerkleAccount> backingAccounts;

	@BeforeEach
	private void setup() {
		fees = mock(FeeCalculator.class);
		grpc = mock(GrpcServerManager.class);
		stats = mock(HederaNodeStats.class);
		pause = mock(Pause.class);
		accounts = mock(FCMap.class);
		topics = mock(FCMap.class);
		storage = mock(FCMap.class);
		mockLog = mock(Logger.class);
		console = mock(Console.class);
		consoleOut = mock(PrintStream.class);
		platform = mock(Platform.class);
		systemExits = mock(SystemExits.class);
		recordStream = mock(RecordStream.class);
		recordStreamThread = mock(Thread.class);
		backingAccounts = (BackingAccounts<AccountID, MerkleAccount>)mock(BackingAccounts.class);
		stateMigrations = mock(StateMigrations.class);
		balancesExporter = mock(BalancesExporter.class);
		recordsHistorian = mock(AccountRecordsHistorian.class);
		ledgerValidator = mock(LedgerValidator.class);
		accountsExporter = mock(AccountsExporter.class);
		propertySanitizer = mock(PropertySanitizer.class);
		platformStatus = mock(CurrentPlatformStatus.class);
		properties = mock(PropertySource.class);
		propertySources = mock(PropertySources.class);
		addressBook = mock(AddressBook.class);
		systemFilesManager = mock(SystemFilesManager.class);
		systemAccountsCreator = mock(SystemAccountsCreator.class);
		ctx = mock(ServicesContext.class);

		ServicesMain.log = mockLog;

		given(ctx.fees()).willReturn(fees);
		given(ctx.stats()).willReturn(stats);
		given(ctx.grpc()).willReturn(grpc);
		given(ctx.pause()).willReturn(pause);
		given(ctx.accounts()).willReturn(accounts);
		given(ctx.id()).willReturn(new NodeId(false, NODE_ID));
		given(ctx.nodeAccount()).willReturn(IdUtils.asAccount("0.0.3"));
		given(ctx.console()).willReturn(console);
		given(ctx.consoleOut()).willReturn(consoleOut);
		given(ctx.addressBook()).willReturn(addressBook);
		given(ctx.platform()).willReturn(platform);
		given(ctx.recordStream()).willReturn(recordStream);
		given(ctx.platformStatus()).willReturn(platformStatus);
		given(ctx.ledgerValidator()).willReturn(ledgerValidator);
		given(ctx.recordStreamThread()).willReturn(recordStreamThread);
		given(ctx.propertySources()).willReturn(propertySources);
		given(ctx.properties()).willReturn(properties);
		given(ctx.recordStream()).willReturn(recordStream);
		given(ctx.stateMigrations()).willReturn(stateMigrations);
		given(ctx.propertySanitizer()).willReturn(propertySanitizer);
		given(ctx.recordsHistorian()).willReturn(recordsHistorian);
		given(ctx.backingAccounts()).willReturn(backingAccounts);
		given(ctx.systemFilesManager()).willReturn(systemFilesManager);
		given(ctx.systemAccountsCreator()).willReturn(systemAccountsCreator);
		given(ctx.accountsExporter()).willReturn(accountsExporter);
		given(ctx.balancesExporter()).willReturn(balancesExporter);
		given(ctx.consensusTimeOfLastHandledTxn()).willReturn(Instant.ofEpochSecond(33L, 0));
		given(properties.getIntProperty("timer.stats.dump.value")).willReturn(123);
		given(properties.getBooleanProperty("timer.stats.dump.started")).willReturn(true);

		subject = new ServicesMain();
		subject.systemExits = systemExits;
		subject.defaultCharset = () -> StandardCharsets.UTF_8;
		CONTEXTS.store(ctx);
	}

	@AfterEach
	public void cleanup() {
		ServicesMain.log = LogManager.getLogger(ServicesMain.class);
	}

	@Test
	public void failsFastOnNonUtf8DefaultCharset() {
		// setup:
		subject.defaultCharset = () -> StandardCharsets.US_ASCII;

		// when:
		subject.init(null, new NodeId(false, NODE_ID));

		// then:
		verify(systemExits).fail(1);
	}

	@Test
	public void failsFastOnMissingNodeAccountIdIfNotSkippingExits() {
		given(ctx.nodeAccount()).willReturn(null);

		// when:
		subject.init(null, new NodeId(false, NODE_ID));

		// then:
		verify(systemExits).fail(1);
	}

	@Test
	public void exitsOnApplicationPropertiesLoading() {
		willThrow(IllegalStateException.class)
				.given(systemFilesManager).loadApplicationProperties();

		// when:
		subject.init(null, new NodeId(false, NODE_ID));

		// then:
		verify(systemExits).fail(1);
	}

	@Test
	public void exitsOnAddressBookCreationFailure() {
		willThrow(IllegalStateException.class)
				.given(systemFilesManager).createAddressBookIfMissing();

		// when:
		subject.init(null, new NodeId(false, NODE_ID));

		// then:
		verify(systemExits).fail(1);
	}

	@Test
	public void exitsOnCreationFailure() {
		willThrow(IllegalStateException.class)
				.given(systemAccountsCreator).ensureSystemAccounts(any(), any());

		// when:
		subject.init(null, new NodeId(false, NODE_ID));

		// then:
		verify(systemExits).fail(1);
	}

	@Test
	public void initializesSanelyGivenPreconditions() {
		// given:
		InOrder inOrder = inOrder(
				propertySources,
				platform,
				stateMigrations,
				ledgerValidator,
				recordStreamThread,
				recordsHistorian,
				fees,
				grpc,
				propertySanitizer);

		// when:
		subject.init(null, new NodeId(false, NODE_ID));

		// then:
		inOrder.verify(platform).addSignedStateListener(any(IssListener.class));
		inOrder.verify(propertySources).assertSourcesArePresent();
		inOrder.verify(platform).setSleepAfterSync(0L);
		inOrder.verify(stateMigrations).runAllFor(ctx);
		inOrder.verify(ledgerValidator).assertIdsAreValid(accounts);
		inOrder.verify(ledgerValidator).hasExpectedTotalBalance(accounts);
		inOrder.verify(recordStreamThread).start();
		inOrder.verify(recordsHistorian).reviewExistingRecords();
		inOrder.verify(fees).init();
		inOrder.verify(propertySanitizer).sanitize(propertySources);

		// cleanup:
		TimerUtils.stopStatsDumpTimer();
	}

	@Test
	public void runsOnDefaultPortInProduction() {
		given(properties.getIntProperty("grpc.port")).willReturn(50211);
		given(properties.getIntProperty("grpc.tlsPort")).willReturn(50212);
		given(properties.getProfileProperty("hedera.profiles.active")).willReturn(Profile.PROD);

		// when:
		subject.init(null, new NodeId(false, NODE_ID));

		// then:
		verify(grpc).start(intThat(i -> i == 50211), intThat(i -> i == 50212), any());
	}

	@Test
	public void runsOnDefaultPortInDevIfBlessedInSingleNodeListeningNode() {
		// setup:
		Address address = mock(Address.class);

		given(properties.getIntProperty("grpc.port")).willReturn(50211);
		given(properties.getIntProperty("grpc.tlsPort")).willReturn(50212);
		given(properties.getProfileProperty("hedera.profiles.active")).willReturn(Profile.DEV);
		given(address.getMemo()).willReturn("0.0.3");
		given(addressBook.getAddress(NODE_ID)).willReturn(address);
		given(properties.getStringProperty("dev.defaultListeningNodeAccount")).willReturn("0.0.3");
		given(properties.getBooleanProperty("dev.onlyDefaultNodeListens")).willReturn(true);

		// when:
		subject.init(null, new NodeId(false, NODE_ID));

		// then:
		verify(grpc).start(intThat(i -> i == 50211), intThat(i -> i == 50212), any());
	}

	@Test
	public void doesntRunInDevIfNotBlessedInSingleNodeListeningNode() {
		// setup:
		Address address = mock(Address.class);

		given(properties.getIntProperty("grpc.port")).willReturn(50211);
		given(properties.getProfileProperty("hedera.profiles.active")).willReturn(Profile.DEV);
		given(address.getMemo()).willReturn("0.0.4");
		given(addressBook.getAddress(NODE_ID)).willReturn(address);
		given(properties.getStringProperty("dev.defaultListeningNodeAccount")).willReturn("0.0.3");
		given(properties.getBooleanProperty("dev.onlyDefaultNodeListens")).willReturn(true);

		// when:
		subject.init(null, new NodeId(false, NODE_ID));

		// then:
		verifyNoInteractions(grpc);
	}

	@Test
	public void runsOnDefaultPortInDevIfNotInSingleNodeListeningNodeAndDefault() {
		// setup:
		Address address = mock(Address.class);

		given(properties.getIntProperty("grpc.port")).willReturn(50211);
		given(properties.getIntProperty("grpc.tlsPort")).willReturn(50212);
		given(properties.getProfileProperty("hedera.profiles.active")).willReturn(Profile.DEV);
		given(address.getMemo()).willReturn("0.0.3");
		given(address.getPortExternalIpv4()).willReturn(50001);
		given(addressBook.getAddress(NODE_ID)).willReturn(address);
		given(properties.getStringProperty("dev.defaultListeningNodeAccount")).willReturn("0.0.3");
		given(properties.getBooleanProperty("dev.onlyDefaultNodeListens")).willReturn(false);

		// when:
		subject.init(null, new NodeId(false, NODE_ID));

		// then:
		verify(grpc).start(intThat(i -> i == 50211), intThat(i -> i == 50212), any());
	}

	@Test
	public void runsOnOffsetPortInDevIfNotInSingleNodeListeningNodeAndNotDefault() {
		// setup:
		Address address = mock(Address.class);

		given(properties.getIntProperty("grpc.port")).willReturn(50211);
		given(properties.getIntProperty("grpc.tlsPort")).willReturn(50212);
		given(properties.getProfileProperty("hedera.profiles.active")).willReturn(Profile.DEV);
		given(address.getMemo()).willReturn("0.0.4");
		given(address.getPortExternalIpv4()).willReturn(50001);
		given(addressBook.getAddress(NODE_ID)).willReturn(address);
		given(properties.getStringProperty("dev.defaultListeningNodeAccount")).willReturn("0.0.3");
		given(properties.getBooleanProperty("dev.onlyDefaultNodeListens")).willReturn(false);

		// when:
		subject.init(null, new NodeId(false, NODE_ID));

		// then:
		verify(grpc).start(intThat(i -> i == 50212), intThat(i -> i == 50213), any());
	}
	
	@Test
	public void managesSystemFiles() {
		// when:
		subject.init(null, new NodeId(false, NODE_ID));

		// then:
		verify(systemFilesManager).createAddressBookIfMissing();
		verify(systemFilesManager).createNodeDetailsIfMissing();
		verify(systemFilesManager).loadApiPermissions();
		verify(systemFilesManager).loadApplicationProperties();
		verify(systemFilesManager).loadExchangeRates();
		verify(systemFilesManager).loadFeeSchedules();
	}

	@Test
	public void createsSystemAccountsIfRequested() throws Exception {
		// when:
		subject.init(null, new NodeId(false, NODE_ID));

		// then:
		verify(systemAccountsCreator).ensureSystemAccounts(backingAccounts, addressBook);
		verify(pause).forMs(ServicesMain.SUGGESTED_POST_CREATION_PAUSE_MS);
	}

	@Test
	public void rethrowsAccountsCreationFailureAsIse() {
		given(ctx.systemAccountsCreator()).willReturn(null);

		// when:
		subject.init(null, new NodeId(false, NODE_ID));

		// then:
		verify(systemExits).fail(1);
	}

	@Test
	public void exportsAccountsIfRequested() throws Exception {
		given(properties.getStringProperty("hedera.accountsExportPath")).willReturn(PATH);
		given(properties.getBooleanProperty("hedera.exportAccountsOnStartup")).willReturn(true);

		// when:
		subject.init(null, new NodeId(false, NODE_ID));

		// then:
		verify(accountsExporter).toFile(accounts, PATH);
	}

	@Test
	public void rethrowsAccountsExportFailureAsIse() {
		given(properties.getStringProperty("hedera.accountsExportPath")).willReturn(PATH);
		given(properties.getBooleanProperty("hedera.exportAccountsOnStartup")).willReturn(true);
		given(ctx.accountsExporter()).willReturn(null);

		// when:
		subject.init(null, new NodeId(false, NODE_ID));

		// then:
		verify(systemExits).fail(1);
	}

	@Test
	public void updatesCurrentStatusOnChangeOnlyIfBehind() {
		// given:
		subject.ctx = ctx;
		PlatformStatus newStatus = PlatformStatus.BEHIND;

		// when:
		subject.platformStatusChange(newStatus);

		// then:
		verify(platformStatus).set(newStatus);
		verifyNoInteractions(recordStream);
	}

	@Test
	public void updatesCurrentStatusAndFreezesRecordStreamOnMaintenance() {
		// given:
		subject.ctx = ctx;
		PlatformStatus newStatus = PlatformStatus.MAINTENANCE;

		// when:
		subject.platformStatusChange(newStatus);

		// then:
		verify(platformStatus).set(newStatus);
		verify(recordStream).setInFreeze(true);
	}

	@Test
	public void updatesCurrentStatusAndFreezesRecordStreamOnActive() {
		// given:
		subject.ctx = ctx;
		PlatformStatus newStatus = PlatformStatus.ACTIVE;

		// when:
		subject.platformStatusChange(newStatus);

		// then:
		verify(platformStatus).set(newStatus);
		verify(recordStream).setInFreeze(false);
	}

	@Test
	public void doesNotPrintHashesIfNotInMaintenance() {
		// setup:
		subject.ctx = ctx;
		var signedState = mock(ServicesState.class);
		var currentPlatformStatus = mock(CurrentPlatformStatus.class);

		given(currentPlatformStatus.get()).willReturn(PlatformStatus.DISCONNECTED);
		given(ctx.platformStatus()).willReturn(currentPlatformStatus);

		// when:
		subject.newSignedState(signedState, Instant.now(), 1L);

		// then:
		verify(signedState, never()).printHashes();
	}

	@Test
	public void onlyPrintsHashesIfInMaintenance() {
		// setup:
		subject.ctx = ctx;
		var signedState = mock(ServicesState.class);
		var currentPlatformStatus = mock(CurrentPlatformStatus.class);

		given(currentPlatformStatus.get()).willReturn(PlatformStatus.MAINTENANCE);
		given(ctx.platformStatus()).willReturn(currentPlatformStatus);

		// when:
		subject.newSignedState(signedState, Instant.now(), 1L);

		// then:
		verify(signedState).printHashes();
	}

	@Test
	public void doesntExportBalanceIfNotTime() throws Exception {
		// setup:
		subject.ctx = ctx;
		Instant when = Instant.now();
		ServicesState signedState = mock(ServicesState.class);

		given(properties.getBooleanProperty("hedera.exportBalancesOnNewSignedState")).willReturn(true);
		given(balancesExporter.isTimeToExport(when)).willReturn(false);

		// when:
		subject.newSignedState(signedState, when, 1L);

		// then:
		verify(balancesExporter, never()).toCsvFile(any(), any());
	}

	@Test
	public void exportsBalancesIfPropertySet() throws Exception {
		// setup:
		subject.ctx = ctx;
		Instant when = Instant.now();
		ServicesState signedState = mock(ServicesState.class);

		given(properties.getBooleanProperty("hedera.exportBalancesOnNewSignedState")).willReturn(true);
		given(balancesExporter.isTimeToExport(when)).willReturn(true);

		// when:
		subject.newSignedState(signedState, when, 1L);

		// then:
		verify(balancesExporter).toCsvFile(signedState, when);
	}

	@Test
	public void doesntExportBalancesIfPropertyNotSet() {
		// setup:
		subject.ctx = ctx;
		Instant when = Instant.now();
		ServicesState signedState = mock(ServicesState.class);

		given(properties.getBooleanProperty("hedera.exportBalancesOnNewSignedState")).willReturn(false);

		// when:
		subject.newSignedState(signedState, when, 1L);

		// then:
		verifyNoInteractions(balancesExporter);
	}

	@Test
	public void failsFastIfBalanceExportDetectedInvalidState() throws Exception {
		// setup:
		subject.ctx = ctx;
		Instant when = Instant.now();
		ServicesState signedState = mock(ServicesState.class);

		given(properties.getBooleanProperty("hedera.exportBalancesOnNewSignedState")).willReturn(true);
		given(balancesExporter.isTimeToExport(when)).willReturn(true);
		willThrow(InvalidTotalAccountBalanceException.class).given(balancesExporter).toCsvFile(signedState, when);

		// when:
		subject.newSignedState(signedState, when, 1L);

		// then:
		verify(systemExits).fail(1);
	}

	@Test
	public void noOpsRun() {
		// expect:
		assertDoesNotThrow(() -> {
			subject.run();
			subject.preEvent();
		});
	}

	@Test
	public void returnsAppState() {
		// expect:
		assertTrue(subject.newState() instanceof ServicesState);
	}
}
