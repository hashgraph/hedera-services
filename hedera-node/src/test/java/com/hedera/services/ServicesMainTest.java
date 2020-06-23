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
import com.hedera.services.context.HederaNodeContext;
import com.hedera.services.context.domain.trackers.IssEventInfo;
import com.hedera.services.context.domain.trackers.IssEventStatus;
import com.hedera.services.context.properties.Profile;
import com.hedera.services.context.properties.PropertySanitizer;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.context.properties.PropertySources;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.files.HederaFs;
import com.hedera.services.files.TieredHederaFs;
import com.hedera.services.grpc.GrpcServerManager;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.legacy.core.StorageKey;
import com.hedera.services.legacy.core.StorageValue;
import com.hedera.services.legacy.core.jproto.JFileInfo;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.handler.FCStorageWrapper;
import com.hedera.services.legacy.handler.FreezeHandler;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hedera.services.legacy.unit.FreezeTestHelper;
import com.hedera.services.records.AccountRecordsHistorian;
import com.hedera.services.state.exports.BalancesExporter;
import com.hedera.services.state.initialization.SystemAccountsCreator;
import com.hedera.services.state.initialization.SystemFilesManager;
import com.hedera.services.state.migration.StateMigrations;
import com.hedera.services.state.validation.LedgerValidator;
import com.hedera.services.state.exports.AccountsExporter;
import com.hedera.services.utils.Pause;
import com.hedera.services.utils.SystemExits;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hedera.services.legacy.exception.InvalidTotalAccountBalanceException;
import com.hedera.services.legacy.services.state.initialization.DefaultSystemAccountsCreator;
import com.hedera.services.legacy.stream.RecordStream;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import com.swirlds.common.Address;
import com.swirlds.common.AddressBook;
import com.swirlds.common.Console;
import com.swirlds.common.InvalidSignedStateListener;
import com.swirlds.common.NodeId;
import com.swirlds.common.Platform;
import com.swirlds.common.PlatformStatus;
import com.swirlds.common.io.FCDataOutputStream;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.codec.binary.Hex;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.*;
import static com.hedera.services.context.SingletonContextsManager.CONTEXTS;

@RunWith(JUnitPlatform.class)
public class ServicesMainTest {
	final long NODE_ID = 1L;
	final long OTHER_NODE_ID = 2L;
	final String PATH = "/this/was/mr/bleaneys/room";

	FCMap topics;
	FCMap accounts;
	FCMap storage;
	Pause pause;
	Thread recordStreamThread;
	Console console;
	Logger mockLog;
	Platform platform;
	SystemExits systemExits;
	AddressBook addressBook;
	PrintStream consoleOut;
	RecordStream recordStream;
	FeeCalculator fees;
	ServicesMain subject;
	ServicesState localSignedState;
	HederaNodeContext ctx;
	PropertySource properties;
	LedgerValidator ledgerValidator;
	AccountsExporter accountsExporter;
	PropertySources propertySources;
	BalancesExporter balancesExporter;
	PropertySanitizer propertySanitizer;
	StateMigrations stateMigrations;
	GrpcServerManager grpc;
	SystemFilesManager systemFilesManager;
	SystemAccountsCreator systemAccountsCreator;
	CurrentPlatformStatus platformStatus;
	AccountRecordsHistorian recordsHistorian;

	@BeforeEach
	private void setup() {
		fees = mock(FeeCalculator.class);
		grpc = mock(GrpcServerManager.class);
		pause = mock(Pause.class);
		accounts = mock(FCMap.class);
		topics = mock(FCMap.class);
		storage = mock(FCMap.class);
		console = mock(Console.class);
		consoleOut = mock(PrintStream.class);
		platform = mock(Platform.class);
		systemExits = mock(SystemExits.class);
		recordStream = mock(RecordStream.class);
		recordStreamThread = mock(Thread.class);
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
		ctx = mock(HederaNodeContext.class);

		given(ctx.fees()).willReturn(fees);
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
		given(ctx.systemFilesManager()).willReturn(systemFilesManager);
		given(ctx.systemAccountsCreator()).willReturn(systemAccountsCreator);
		given(ctx.accountsExporter()).willReturn(accountsExporter);
		given(ctx.balancesExporter()).willReturn(balancesExporter);
		given(ctx.consensusTimeOfLastHandledTxn()).willReturn(Instant.ofEpochSecond(33L, 0));
		given(properties.getBooleanProperty("hedera.exitOnNodeStartupFailure")).willReturn(true);

		subject = new ServicesMain();
		subject.systemExits = systemExits;
		subject.defaultCharset = () -> StandardCharsets.UTF_8;
		CONTEXTS.store(ctx);
	}

	@Test
	public void doesntFailFastOnMissingNodeAccountIdIfSkippingExits() {
		given(properties.getBooleanProperty("hedera.exitOnNodeStartupFailure")).willReturn(false);
		given(ctx.nodeAccount()).willReturn(null);

		// when:
		subject.init(null, new NodeId(false, NODE_ID));

		// then:
		verify(systemExits, never()).fail(1);
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
		inOrder.verify(platform).addSignedStateListener(any());
		inOrder.verify(propertySources).assertSourcesArePresent();
		inOrder.verify(platform).setSleepAfterSync(0L);
		inOrder.verify(stateMigrations).runAllFor(ctx);
		inOrder.verify(ledgerValidator).assertIdsAreValid(accounts);
		inOrder.verify(ledgerValidator).hasExpectedTotalBalance(accounts);
		inOrder.verify(recordStreamThread).start();
		inOrder.verify(recordsHistorian).reviewExistingRecords(33L);
		inOrder.verify(fees).init();
		inOrder.verify(propertySanitizer).sanitize(propertySources);
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
	public void managesSystemFiles() throws Exception {
		given(properties.getBooleanProperty("hedera.createSystemFilesOnStartup")).willReturn(true);

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
		given(properties.getBooleanProperty("hedera.createSystemAccountsOnStartup")).willReturn(true);

		// when:
		subject.init(null, new NodeId(false, NODE_ID));

		// then:
		verify(systemAccountsCreator).createSystemAccounts(accounts, addressBook);
		verify(pause).forMs(DefaultSystemAccountsCreator.SUGGESTED_POST_CREATION_PAUSE_MS);
	}

	@Test
	public void skipsSystemAccountCreationIfNotRequested() {
		given(properties.getBooleanProperty("hedera.createSystemAccountsOnStartup")).willReturn(false);

		// when:
		subject.init(null, new NodeId(false, NODE_ID));

		// then:
		verifyNoInteractions(systemAccountsCreator);
	}

	@Test
	public void rethrowsAccountsCreationFailureAsIse() {
		given(properties.getBooleanProperty("hedera.createSystemAccountsOnStartup")).willReturn(true);
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

	@Test
	public void doesntDumpIfOngoingIss() throws Exception {
		// setup:
		byte[] topicRootHash = "sdfgf".getBytes();
		String trHashHex = Hex.encodeHexString(topicRootHash);
		byte[] storageRootHash = "fdsa".getBytes();
		String srHashHex = Hex.encodeHexString(storageRootHash);
		byte[] accountsRootHash = "asdf".getBytes();
		String acHashHex = Hex.encodeHexString(accountsRootHash);
		var round = 1_234L;
		var mockIssInfo = mock(IssEventInfo.class);
		NodeId self = new NodeId(false, NODE_ID);
		NodeId other = new NodeId(false, OTHER_NODE_ID);
		byte[] hash = "xyz".getBytes();
		String hashHex = Hex.encodeHexString(hash);
		byte[] sig = "zyx".getBytes();
		// and:
		ArgumentCaptor<InvalidSignedStateListener> captor = ArgumentCaptor.forClass(InvalidSignedStateListener.class);
		Instant consensusTime = Instant.now();

		given(mockIssInfo.status()).willReturn(IssEventStatus.NO_KNOWN_ISS);
		given(mockIssInfo.shouldDumpThisRound()).willReturn(false);
		given(ctx.issEventInfo()).willReturn(mockIssInfo);
		given(accounts.getRootHash()).willReturn(accountsRootHash);
		given(storage.getRootHash()).willReturn(storageRootHash);
		given(topics.getRootHash()).willReturn(topicRootHash);
		// and:
		localSignedState = mock(ServicesState.class);
		given(localSignedState.getAccountMap()).willReturn(accounts);
		given(localSignedState.getStorageMap()).willReturn(storage);
		given(localSignedState.getTopicsMap()).willReturn(topics);
		// and:
		subject.init(null, new NodeId(false, NODE_ID));

		// when:
		verify(platform).addSignedStateListener(captor.capture());
		// and:
		captor.getValue().notifyError(
				platform,
				addressBook,
				localSignedState,
				null,
				self,
				other,
				round,
				consensusTime,
				1_234_567L,
				sig,
				hash);

		// then:
		verify(accounts, never()).copyTo(any());
		verify(storage, never()).copyTo(any());
		verify(topics, never()).copyTo(any());
	}

	@Test
	public void logsExpectedIssInfo() throws Exception {
		// setup:
		var round = 1_234L;
		byte[] topicRootHash = "sdfgf".getBytes();
		String trHashHex = Hex.encodeHexString(topicRootHash);
		byte[] storageRootHash = "fdsa".getBytes();
		String srHashHex = Hex.encodeHexString(storageRootHash);
		byte[] accountsRootHash = "asdf".getBytes();
		String acHashHex = Hex.encodeHexString(accountsRootHash);
		// and:
		byte[] hash = "xyz".getBytes();
		String hashHex = Hex.encodeHexString(hash);
		byte[] sig = "zyx".getBytes();
		String sigHex = Hex.encodeHexString(sig);
		// and:
		var mockIssInfo = mock(IssEventInfo.class);
		given(mockIssInfo.shouldDumpThisRound()).willReturn(true);
		given(ctx.issEventInfo()).willReturn(mockIssInfo);
		mockLog = mock(Logger.class);
		ServicesMain.log = mockLog;
		localSignedState = mock(ServicesState.class);
		NodeId self = new NodeId(false, NODE_ID);
		NodeId other = new NodeId(false, OTHER_NODE_ID);
		// and:
		ArgumentCaptor<InvalidSignedStateListener> captor = ArgumentCaptor.forClass(InvalidSignedStateListener.class);
		Instant consensusTime = Instant.now();
		// and:
		FCDataOutputStream foutAccounts = mock(FCDataOutputStream.class);
		FCDataOutputStream foutStorage = mock(FCDataOutputStream.class);
		FCDataOutputStream foutTopics = mock(FCDataOutputStream.class);
		Function<String, FCDataOutputStream> supplier = (Function<String, FCDataOutputStream>)mock(Function.class);
		subject.foutSupplier = supplier;
		// and:
		InOrder inOrder = inOrder(accounts, storage, topics, foutAccounts, foutStorage, foutTopics, mockIssInfo);

		given(accounts.getRootHash()).willReturn(accountsRootHash);
		given(storage.getRootHash()).willReturn(storageRootHash);
		given(topics.getRootHash()).willReturn(topicRootHash);
		// and:
		given(localSignedState.getAccountMap()).willReturn(accounts);
		given(localSignedState.getStorageMap()).willReturn(storage);
		given(localSignedState.getTopicsMap()).willReturn(topics);
		// and:
		given(supplier.apply(
				String.format(ServicesMain.FC_DUMP_LOC_TPL,
						subject.getClass().getName(), self.getId(), "accounts", round)))
				.willReturn(foutAccounts);
		given(supplier.apply(
				String.format(ServicesMain.FC_DUMP_LOC_TPL,
						subject.getClass().getName(), self.getId(), "storage", round)))
				.willReturn(foutStorage);
		given(supplier.apply(
				String.format(ServicesMain.FC_DUMP_LOC_TPL,
						subject.getClass().getName(), self.getId(), "topics", round)))
				.willReturn(foutTopics);
		// and:
		subject.init(null, new NodeId(false, NODE_ID));

		// when:
		verify(platform).addSignedStateListener(captor.capture());
		// and:
		captor.getValue().notifyError(
				platform,
				addressBook,
				localSignedState,
				null,
				self,
				other,
				round,
				consensusTime,
				1_234_567L,
				sig,
				hash);

		// then:
		String msg = String.format(
				ServicesMain.ISS_ERROR_MSG_PATTERN,
				round, NODE_ID, OTHER_NODE_ID, sigHex, hashHex, acHashHex, srHashHex, trHashHex);
		verify(mockLog).error(msg);
		// and
		inOrder.verify(mockIssInfo).alert(consensusTime);
		// and:
		inOrder.verify(accounts).copyTo(foutAccounts);
		inOrder.verify(accounts).copyToExtra(foutAccounts);
		inOrder.verify(foutAccounts).close();
		inOrder.verify(storage).copyTo(foutStorage);
		inOrder.verify(storage).copyToExtra(foutStorage);
		inOrder.verify(foutStorage).close();
		inOrder.verify(topics).copyTo(foutTopics);
		inOrder.verify(topics).copyToExtra(foutTopics);
		inOrder.verify(foutTopics).close();
	}

	@Test
	public void foutSupplierWorks() throws Exception {
		// given:
		var okPath = "src/test/resources/tmp.nothing";

		// when:
		var fout = subject.foutSupplier.apply(okPath);
		// and:
		assertDoesNotThrow(() -> fout.writeUTF("Here is something"));
		(new File(okPath)).delete();
	}

	@Test
	public void foutSupplierDoesntBlowUp() throws Exception {
		// given:
		var badPath = "this/path/does/not/exist";

		// when:
		var fout = subject.foutSupplier.apply(badPath);
		// and:
		assertDoesNotThrow(() -> fout.writeUTF("Here is something"));
	}

	@Test
	public void logsFallbackIssInfoOnException() {
		// setup:
		mockLog = mock(Logger.class);
		ServicesMain.log = mockLog;
		// and:
		ArgumentCaptor<InvalidSignedStateListener> captor = ArgumentCaptor.forClass(InvalidSignedStateListener.class);
		Instant consensusTime = Instant.now();

		// and:
		subject.init(null, new NodeId(false, NODE_ID));

		// when:
		verify(platform).addSignedStateListener(captor.capture());
		// and:
		captor.getValue().notifyError(
				null,
				null,
				null,
				null,
				null,
				null,
				1,
				null,
				2,
				null,
				null);

		// then:
		String msg = String.format(ServicesMain.ISS_FALLBACK_ERROR_MSG_PATTERN, 1, "null", "null");
		verify(mockLog).error((String)argThat(msg::equals), any(Exception.class));
	}

	@Test
	public void createUsesNextEntityId() throws Throwable {
		EntityIdSource ids = mock(EntityIdSource.class);
		AccountID sponsor = IdUtils.asAccount("0.0.2");
		FileID fid = IdUtils.asFile("0.0.7575");

		Map<FileID, byte[]>data = mock(Map.class);
		Map<FileID, JFileInfo> metadata = mock(Map.class);
		Supplier<Instant> clock = mock(Supplier.class);

		given(ids.newFileId(sponsor)).willReturn(fid);

		TieredHederaFs fileSystemSubject = new TieredHederaFs(ids, properties, clock, data, metadata);
		int lifetimeSecs = 1_234_567;
		JKey validKey = TxnHandlingScenario.MISC_FILE_WACL_KT.asJKey();
		Instant now = Instant.now();

		JFileInfo livingAttr = new JFileInfo(false, validKey, now.getEpochSecond() + lifetimeSecs);
		String zipFile = "src/test/resources/testfiles/updateSettings/update.zip";

		try {
			byte[] origContents = Files.readAllBytes(Paths.get(zipFile));
			// when:
			var newFile = fileSystemSubject.create(origContents, livingAttr, sponsor);

			// then:
			assertEquals(fid, newFile);
			verify(data).put(fid, origContents);
			verify(metadata).put(fid, livingAttr);
		} catch (IOException e) {
			Assert.fail("Error creating file: reading contract file " + zipFile);
		}
	}

	@Test
	public void freezeAndUpdateSettings() {
		String fsPath = "/0/a1234/";
		String zipFile = "src/test/resources/testfiles/updateSettings/update.zip";
		try {
			byte[] data = Files.readAllBytes(Paths.get(zipFile));
			FCMap<StorageKey, StorageValue> storageMap = new FCMap<>(StorageKey::deserialize, StorageValue::deserialize);;

			FCStorageWrapper storageWrapper = new FCStorageWrapper(storageMap);
			storageWrapper.fileCreate(fsPath, data, 1000,
					2, 3, null);

			FreezeHandler freezeHandler;
			Instant consensusTime = new Date().toInstant();
			HederaFs hfs;
			hfs = mock(HederaFs.class);
			platform = Mockito.mock(Platform.class);
			freezeHandler = new FreezeHandler(hfs, platform);
			Transaction transaction = FreezeTestHelper.createFreezeTransaction(true, true, null);
			TransactionBody txBody = CommonUtils.extractTransactionBody(transaction);
			TransactionRecord record = freezeHandler.freeze(txBody, consensusTime);
			Assertions.assertEquals( record.getReceipt().getStatus() , ResponseCodeEnum.SUCCESS);

			// given:
			subject.ctx = ctx;
			PlatformStatus newStatus = PlatformStatus.MAINTENANCE;

			// when:
			subject.platformStatusChange(newStatus);

			// then:
			verify(platformStatus).set(newStatus);
			verify(recordStream).setInFreeze(true);
		} catch (IOException e) {
			Assert.fail("Error creating file: reading contract file " + zipFile);
		}

	}
}
