package com.hedera.services.context;

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

import com.hedera.services.ServicesState;
import com.hedera.services.config.AccountNumbers;
import com.hedera.services.config.EntityNumbers;
import com.hedera.services.config.FileNumbers;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.context.properties.SemanticVersions;
import com.hedera.services.fees.AwareHbarCentExchange;
import com.hedera.services.fees.StandardExemptions;
import com.hedera.services.grpc.controllers.ContractController;
import com.hedera.services.grpc.controllers.FreezeController;
import com.hedera.services.grpc.controllers.ScheduleController;
import com.hedera.services.grpc.controllers.TokenController;
import com.hedera.services.keys.LegacyEd25519KeyReader;
import com.hedera.services.ledger.accounts.BackingTokenRels;
import com.hedera.services.ledger.accounts.FCMapBackingAccounts;
import com.hedera.services.legacy.core.jproto.JFileInfo;
import com.hedera.services.queries.answering.ZeroStakeAnswerFlow;
import com.hedera.services.queries.contract.ContractAnswers;
import com.hedera.services.queries.schedule.ScheduleAnswers;
import com.hedera.services.queries.token.TokenAnswers;
import com.hedera.services.security.ops.SystemOpPolicies;
import com.hedera.services.state.expiry.ExpiringCreations;
import com.hedera.services.state.expiry.ExpiryManager;
import com.hedera.services.state.exports.SignedStateBalancesExporter;
import com.hedera.services.state.initialization.BackedSystemAccountsCreator;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleDiskFs;
import com.hedera.services.state.merkle.MerkleEntityAssociation;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.context.domain.trackers.ConsensusStatusCounts;
import com.hedera.services.context.domain.trackers.IssEventInfo;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.context.properties.PropertySources;
import com.hedera.services.contracts.execution.SolidityLifecycle;
import com.hedera.services.contracts.execution.TxnAwareSoliditySigsVerifier;
import com.hedera.services.contracts.persistence.BlobStoragePersistence;
import com.hedera.services.fees.calculation.AwareFcfsUsagePrices;
import com.hedera.services.fees.calculation.UsageBasedFeeCalculator;
import com.hedera.services.fees.charging.ItemizableFeeCharging;
import com.hedera.services.fees.charging.TxnFeeChargingPolicy;
import com.hedera.services.files.TieredHederaFs;
import com.hedera.services.files.interceptors.FeeSchedulesManager;
import com.hedera.services.files.interceptors.TxnAwareRatesManager;
import com.hedera.services.files.interceptors.ValidatingCallbackInterceptor;
import com.hedera.services.files.store.FcBlobsBytesStore;
import com.hedera.services.grpc.NettyGrpcServerManager;
import com.hedera.services.grpc.controllers.ConsensusController;
import com.hedera.services.grpc.controllers.CryptoController;
import com.hedera.services.grpc.controllers.FileController;
import com.hedera.services.grpc.controllers.NetworkController;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.ids.SeqNoEntityIdSource;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleBlobMeta;
import com.hedera.services.state.merkle.MerkleOptionalBlob;
import com.hedera.services.queries.answering.StakedAnswerFlow;
import com.hedera.services.queries.consensus.HcsAnswers;
import com.hedera.services.queries.validation.QueryFeeCheck;
import com.hedera.services.state.initialization.HfsSystemFilesManager;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hedera.services.state.validation.BasedLedgerValidator;
import com.hedera.services.stats.HapiOpCounters;
import com.hedera.services.stats.MiscRunningAvgs;
import com.hedera.services.stats.MiscSpeedometers;
import com.hedera.services.stats.ServicesStatsManager;
import com.hedera.services.throttling.BucketThrottling;
import com.hedera.services.throttling.TransactionThrottling;
import com.hedera.services.tokens.HederaTokenStore;
import com.hedera.services.txns.TransitionLogicLookup;
import com.hedera.services.txns.submission.PlatformSubmissionManager;
import com.hedera.services.txns.submission.TxnHandlerSubmissionFlow;
import com.hedera.services.txns.submission.TxnResponseHelper;
import com.hedera.services.txns.validation.ContextOptionValidator;
import com.hedera.services.queries.answering.AnswerFunctions;
import com.hedera.services.queries.answering.QueryResponseHelper;
import com.hedera.services.queries.crypto.CryptoAnswers;
import com.hedera.services.queries.meta.MetaAnswers;
import com.hedera.services.records.TxnAwareRecordsHistorian;
import com.hedera.services.records.RecordCache;
import com.hedera.services.sigs.order.HederaSigningOrder;
import com.hedera.services.sigs.verification.PrecheckVerifier;
import com.hedera.services.sigs.verification.SyncVerifier;
import com.hedera.services.state.migration.StdStateMigrations;
import com.hedera.services.utils.SleepingPause;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hedera.services.legacy.handler.FreezeHandler;
import com.hedera.services.legacy.handler.SmartContractRequestHandler;
import com.hedera.services.legacy.handler.TransactionHandler;
import com.hedera.services.contracts.sources.LedgerAccountsSource;
import com.hedera.services.contracts.sources.BlobStorageSource;
import com.hedera.services.legacy.services.state.AwareProcessLogic;
import com.hedera.services.legacy.services.utils.DefaultAccountsExporter;
import com.hedera.services.legacy.stream.RecordStream;
import com.swirlds.common.Address;
import com.swirlds.common.AddressBook;
import com.swirlds.common.Console;
import com.swirlds.common.NodeId;
import com.swirlds.common.Platform;
import com.swirlds.common.PlatformStatus;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.fcmap.FCMap;
import org.ethereum.db.ServicesRepositoryRoot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.*;
import static org.hamcrest.MatcherAssert.assertThat;

@RunWith(JUnitPlatform.class)
public class ServicesContextTest {
	private final NodeId id = new NodeId(false, 1L);

	RichInstant consensusTimeOfLastHandledTxn = RichInstant.fromJava(Instant.now());
	Platform platform;
	SequenceNumber seqNo;
	ExchangeRates midnightRates;
	MerkleNetworkContext networkCtx;
	ServicesState state;
	Cryptography crypto;
	PropertySource properties;
	PropertySources propertySources;
	FCMap<MerkleEntityId, MerkleTopic> topics;
	FCMap<MerkleEntityId, MerkleToken> tokens;
	FCMap<MerkleEntityId, MerkleAccount> accounts;
	FCMap<MerkleBlobMeta, MerkleOptionalBlob> storage;
	FCMap<MerkleEntityAssociation, MerkleTokenRelStatus> tokenAssociations;

	@BeforeEach
	void setup() {
		topics = mock(FCMap.class);
		tokens = mock(FCMap.class);
		tokenAssociations = mock(FCMap.class);
		storage = mock(FCMap.class);
		accounts = mock(FCMap.class);
		seqNo = mock(SequenceNumber.class);
		midnightRates = mock(ExchangeRates.class);
		networkCtx = new MerkleNetworkContext(consensusTimeOfLastHandledTxn, seqNo, midnightRates);
		state = mock(ServicesState.class);
		given(state.networkCtx()).willReturn(networkCtx);
		given(state.accounts()).willReturn(accounts);
		given(state.storage()).willReturn(storage);
		given(state.topics()).willReturn(topics);
		given(state.tokens()).willReturn(tokens);
		given(state.tokenAssociations()).willReturn(tokenAssociations);
		crypto = mock(Cryptography.class);
		platform = mock(Platform.class);
		given(platform.getCryptography()).willReturn(crypto);
		properties = mock(PropertySource.class);
		propertySources = mock(PropertySources.class);
		given(propertySources.asResolvingSource()).willReturn(properties);
	}

	@Test
	public void updatesStateAsExpected() {
		// setup:
		var newState = mock(ServicesState.class);
		var newAccounts = mock(FCMap.class);
		var newTopics = mock(FCMap.class);
		var newStorage = mock(FCMap.class);
		var newTokens = mock(FCMap.class);
		var newTokenRels = mock(FCMap.class);

		given(newState.accounts()).willReturn(newAccounts);
		given(newState.topics()).willReturn(newTopics);
		given(newState.tokens()).willReturn(newTokens);
		given(newState.storage()).willReturn(newStorage);
		given(newState.tokenAssociations()).willReturn(newTokenRels);
		// given:
		var subject = new ServicesContext(id, platform, state, propertySources);
		// and:
		var accountsRef = subject.queryableAccounts();
		var topicsRef = subject.queryableTopics();
		var storageRef = subject.queryableStorage();
		var tokensRef = subject.queryableTokens();
		var tokenRelsRef = subject.queryableTokenAssociations();

		// when:
		subject.update(newState);

		// then:
		assertSame(newState, subject.state);
		assertSame(accountsRef, subject.queryableAccounts());
		assertSame(topicsRef, subject.queryableTopics());
		assertSame(storageRef, subject.queryableStorage());
		assertSame(tokensRef, subject.queryableTokens());
		assertSame(tokenRelsRef, subject.queryableTokenAssociations());
		// and:
		assertSame(newAccounts, subject.queryableAccounts().get());
		assertSame(newTopics, subject.queryableTopics().get());
		assertSame(newStorage, subject.queryableStorage().get());
		assertSame(newTokens, subject.queryableTokens().get());
		assertSame(newTokenRels, subject.queryableTokenAssociations().get());
	}

	@Test
	public void delegatesPrimitivesToState() {
		// setup:
		InOrder inOrder = inOrder(state);

		// given:
		var subject = new ServicesContext(id, platform, state, propertySources);

		// when:
		subject.addressBook();
		var actualSeqNo = subject.seqNo();
		var actualMidnightRates = subject.midnightRates();
		var actualLastHandleTime = subject.consensusTimeOfLastHandledTxn();
		subject.topics();
		subject.storage();
		subject.accounts();

		// then:
		inOrder.verify(state).addressBook();
		assertEquals(seqNo, actualSeqNo);
		assertEquals(midnightRates, actualMidnightRates);
		assertEquals(consensusTimeOfLastHandledTxn.toJava(), actualLastHandleTime);
		inOrder.verify(state).topics();
		inOrder.verify(state).storage();
		inOrder.verify(state).accounts();
	}

	@Test
	public void hasExpectedNodeAccount() {
		// setup:
		Address address = mock(Address.class);
		AddressBook book = mock(AddressBook.class);

		given(address.getMemo()).willReturn("0.0.3");
		given(book.getAddress(1L)).willReturn(address);
		given(state.addressBook()).willReturn(book);

		// when:
		ServicesContext ctx = new ServicesContext(id, platform, state, propertySources);

		// then:
		assertEquals(ctx.address(), address);
		assertEquals(AccountID.newBuilder().setAccountNum(3L).build(), ctx.nodeAccount());
	}

	@Test
	public void canOverrideLastHandledConsensusTime() {
		// given:
		Instant dataDrivenNow = Instant.now();
		ServicesContext ctx =
				new ServicesContext(
						id,
						platform,
						state,
						propertySources);

		// when:
		ctx.updateConsensusTimeOfLastHandledTxn(dataDrivenNow);

		// then:
		assertEquals(dataDrivenNow, ctx.consensusTimeOfLastHandledTxn());
	}

	@Test
	public void hasExpectedConsole() {
		// setup:
		Console console = mock(Console.class);
		given(platform.createConsole(true)).willReturn(console);

		// when:
		ServicesContext ctx = new ServicesContext(id, platform, state, propertySources);

		// then:
		assertEquals(console, ctx.console());
		assertNull(ctx.consoleOut());
	}

	@Test
	public void hasExpectedZeroStakeInfrastructure() {
		// setup:
		Address address = mock(Address.class);
		AddressBook book = mock(AddressBook.class);
		given(address.getMemo()).willReturn("0.0.3");
		given(address.getStake()).willReturn(0L);
		given(book.getAddress(1L)).willReturn(address);
		given(state.addressBook()).willReturn(book);

		// given:
		ServicesContext ctx = new ServicesContext(id, platform, state, propertySources);

		// expect:
		assertEquals(ServicesNodeType.ZERO_STAKE_NODE, ctx.nodeType());
		// and:
		assertThat(ctx.answerFlow(), instanceOf(ZeroStakeAnswerFlow.class));
	}

	@Test
	public void rebuildsBackingAccountsIfNonNull() {
		// setup:
		BackingTokenRels tokenRels = mock(BackingTokenRels.class);
		FCMapBackingAccounts backingAccounts = mock(FCMapBackingAccounts.class);

		// given:
		ServicesContext ctx = new ServicesContext(id, platform, state, propertySources);

		// expect:
		assertDoesNotThrow(ctx::rebuildBackingStoresIfPresent);

		// and given:
		ctx.setBackingAccounts(backingAccounts);
		ctx.setBackingTokenRels(tokenRels);

		// when:
		ctx.rebuildBackingStoresIfPresent();

		// then:
		verify(tokenRels).rebuildFromSources();
		verify(backingAccounts).rebuildFromSources();
	}

	@Test
	public void hasExpectedStakedInfrastructure() {
		// setup:
		Address address = mock(Address.class);
		AddressBook book = mock(AddressBook.class);
		given(address.getMemo()).willReturn("0.0.3");
		given(address.getStake()).willReturn(1_234_567L);
		given(book.getAddress(1L)).willReturn(address);
		given(state.addressBook()).willReturn(book);
		given(properties.getStringProperty("hedera.recordStream.logDir")).willReturn("src/main/resources");

		// given:
		ServicesContext ctx = new ServicesContext(id, platform, state, propertySources);
		// and:
		ctx.platformStatus().set(PlatformStatus.DISCONNECTED);

		// expect:
		assertEquals(SleepingPause.SLEEPING_PAUSE, ctx.pause());
		assertEquals(PlatformStatus.DISCONNECTED, ctx.platformStatus().get());
		assertEquals("record_stream_0.0.3", ctx.recordStreamThread().getName());
		assertEquals(ctx.properties(), properties);
		assertEquals(ctx.propertySources(), propertySources);
		// and expect TDD:
		assertThat(ctx.hfs(), instanceOf(TieredHederaFs.class));
		assertThat(ctx.ids(), instanceOf(SeqNoEntityIdSource.class));
		assertThat(ctx.fees(), instanceOf(UsageBasedFeeCalculator.class));
		assertThat(ctx.grpc(), instanceOf(NettyGrpcServerManager.class));
		assertThat(ctx.ledger(), instanceOf(HederaLedger.class));
		assertThat(ctx.txnCtx(), instanceOf(AwareTransactionContext.class));
		assertThat(ctx.keyOrder(), instanceOf(HederaSigningOrder.class));
		assertThat(ctx.backedKeyOrder(), instanceOf(HederaSigningOrder.class));
		assertThat(ctx.validator(), instanceOf(ContextOptionValidator.class));
		assertThat(ctx.hcsAnswers(), instanceOf(HcsAnswers.class));
		assertThat(ctx.issEventInfo(), instanceOf(IssEventInfo.class));
		assertThat(ctx.cryptoGrpc(), instanceOf(CryptoController.class));
		assertThat(ctx.answerFlow(), instanceOf(StakedAnswerFlow.class));
		assertThat(ctx.recordCache(), instanceOf(RecordCache.class));
		assertThat(ctx.topics(), instanceOf(FCMap.class));
		assertThat(ctx.storage(), instanceOf(FCMap.class));
		assertThat(ctx.metaAnswers(), instanceOf(MetaAnswers.class));
		assertThat(ctx.stateViews().get(), instanceOf(StateView.class));
		assertThat(ctx.fileNums(), instanceOf(FileNumbers.class));
		assertThat(ctx.accountNums(), instanceOf(AccountNumbers.class));
		assertThat(ctx.usagePrices(), instanceOf(AwareFcfsUsagePrices.class));
		assertThat(ctx.currentView(), instanceOf(StateView.class));
		assertThat(ctx.blobStore(), instanceOf(FcBlobsBytesStore.class));
		assertThat(ctx.entityExpiries(), instanceOf(Map.class));
		assertThat(ctx.syncVerifier(), instanceOf(SyncVerifier.class));
		assertThat(ctx.txnThrottling(), instanceOf(TransactionThrottling.class));
		assertThat(ctx.bucketThrottling(), instanceOf(BucketThrottling.class));
		assertThat(ctx.accountSource(), instanceOf(LedgerAccountsSource.class));
		assertThat(ctx.bytecodeDb(), instanceOf(BlobStorageSource.class));
		assertThat(ctx.cryptoAnswers(), instanceOf(CryptoAnswers.class));
		assertThat(ctx.tokenAnswers(), instanceOf(TokenAnswers.class));
		assertThat(ctx.scheduleAnswers(), instanceOf(ScheduleAnswers.class));
		assertThat(ctx.consensusGrpc(), instanceOf(ConsensusController.class));
		assertThat(ctx.storagePersistence(), instanceOf(BlobStoragePersistence.class));
		assertThat(ctx.filesGrpc(), instanceOf(FileController.class));
		assertThat(ctx.networkGrpc(), instanceOf(NetworkController.class));
		assertThat(ctx.entityNums(), instanceOf(EntityNumbers.class));
		assertThat(ctx.feeSchedulesManager(), instanceOf(FeeSchedulesManager.class));
		assertThat(ctx.submissionFlow(), instanceOf(TxnHandlerSubmissionFlow.class));
		assertThat(ctx.answerFunctions(), instanceOf(AnswerFunctions.class));
		assertThat(ctx.queryFeeCheck(), instanceOf(QueryFeeCheck.class));
		assertThat(ctx.queryableTopics(), instanceOf(AtomicReference.class));
		assertThat(ctx.transitionLogic(), instanceOf(TransitionLogicLookup.class));
		assertThat(ctx.precheckVerifier(), instanceOf(PrecheckVerifier.class));
		assertThat(ctx.apiPermissionsReloading(), instanceOf(ValidatingCallbackInterceptor.class));
		assertThat(ctx.applicationPropertiesReloading(), instanceOf(ValidatingCallbackInterceptor.class));
		assertThat(ctx.recordsHistorian(), instanceOf(TxnAwareRecordsHistorian.class));
		assertThat(ctx.queryableAccounts(), instanceOf(AtomicReference.class));
		assertThat(ctx.txnChargingPolicy(), instanceOf(TxnFeeChargingPolicy.class));
		assertThat(ctx.txnResponseHelper(), instanceOf(TxnResponseHelper.class));
		assertThat(ctx.statusCounts(), instanceOf(ConsensusStatusCounts.class));
		assertThat(ctx.queryableStorage(), instanceOf(AtomicReference.class));
		assertThat(ctx.systemFilesManager(), instanceOf(HfsSystemFilesManager.class));
		assertThat(ctx.queryResponseHelper(), instanceOf(QueryResponseHelper.class));
		assertThat(ctx.solidityLifecycle(), instanceOf(SolidityLifecycle.class));
		assertThat(ctx.charging(), instanceOf(ItemizableFeeCharging.class));
		assertThat(ctx.repository(), instanceOf(ServicesRepositoryRoot.class));
		assertThat(ctx.newPureRepo(), instanceOf(Supplier.class));
		assertThat(ctx.exchangeRatesManager(), instanceOf(TxnAwareRatesManager.class));
		assertThat(ctx.lookupRetryingKeyOrder(), instanceOf(HederaSigningOrder.class));
		assertThat(ctx.soliditySigsVerifier(), instanceOf(TxnAwareSoliditySigsVerifier.class));
		assertThat(ctx.expiries(), instanceOf(ExpiryManager.class));
		assertThat(ctx.creator(), instanceOf(ExpiringCreations.class));
		assertThat(ctx.txnHistories(), instanceOf(Map.class));
		assertThat(ctx.backingAccounts(), instanceOf(FCMapBackingAccounts.class));
		assertThat(ctx.backingTokenRels(), instanceOf(BackingTokenRels.class));
		assertThat(ctx.systemAccountsCreator(), instanceOf(BackedSystemAccountsCreator.class));
		assertThat(ctx.b64KeyReader(), instanceOf(LegacyEd25519KeyReader.class));
		assertThat(ctx.ledgerValidator(), instanceOf(BasedLedgerValidator.class));
		assertThat(ctx.systemOpPolicies(), instanceOf(SystemOpPolicies.class));
		assertThat(ctx.exemptions(), instanceOf(StandardExemptions.class));
		assertThat(ctx.submissionManager(), instanceOf(PlatformSubmissionManager.class));
		assertThat(ctx.platformStatus(), instanceOf(ContextPlatformStatus.class));
		assertThat(ctx.contractAnswers(), instanceOf(ContractAnswers.class));
		assertThat(ctx.tokenStore(), instanceOf(HederaTokenStore.class));
		assertThat(ctx.globalDynamicProperties(), instanceOf(GlobalDynamicProperties.class));
		assertThat(ctx.tokenGrpc(), instanceOf(TokenController.class));
		assertThat(ctx.scheduleGrpc(), instanceOf(ScheduleController.class));
		assertThat(ctx.nodeLocalProperties(), instanceOf(NodeLocalProperties.class));
		assertThat(ctx.balancesExporter(), instanceOf(SignedStateBalancesExporter.class));
		assertThat(ctx.exchange(), instanceOf(AwareHbarCentExchange.class));
		assertThat(ctx.stateMigrations(), instanceOf(StdStateMigrations.class));
		assertThat(ctx.opCounters(), instanceOf(HapiOpCounters.class));
		assertThat(ctx.runningAvgs(), instanceOf(MiscRunningAvgs.class));
		assertThat(ctx.speedometers(), instanceOf(MiscSpeedometers.class));
		assertThat(ctx.statsManager(), instanceOf(ServicesStatsManager.class));
		assertThat(ctx.semVers(), instanceOf(SemanticVersions.class));
		assertThat(ctx.freezeGrpc(), instanceOf(FreezeController.class));
		assertThat(ctx.contractsGrpc(), instanceOf(ContractController.class));
		// and:
		assertEquals(ServicesNodeType.STAKED_NODE, ctx.nodeType());
		// and expect legacy:
		assertThat(ctx.txns(), instanceOf(TransactionHandler.class));
		assertThat(ctx.contracts(), instanceOf(SmartContractRequestHandler.class));
		assertThat(ctx.recordStream(), instanceOf(RecordStream.class));
		assertThat(ctx.accountsExporter(), instanceOf(DefaultAccountsExporter.class));
		assertThat(ctx.freeze(), instanceOf(FreezeHandler.class));
		assertThat(ctx.logic(), instanceOf(AwareProcessLogic.class));
	}

	@Test
	public void shouldInitFees() throws Exception {
		given(properties.getLongProperty("files.feeSchedules")).willReturn(111L);
		given(properties.getIntProperty("cache.records.ttl")).willReturn(180);
		var book = mock(AddressBook.class);
		var diskFs = mock(MerkleDiskFs.class);
		var blob = mock(MerkleOptionalBlob.class);
		byte[] fileInfo = new JFileInfo(false, StateView.EMPTY_WACL, 1_234_567L).serialize();
		byte[] fileContents = new byte[0];
		given(state.addressBook()).willReturn(book);
		given(state.diskFs()).willReturn(diskFs);
		given(storage.containsKey(any())).willReturn(true);
		given(storage.get(any())).willReturn(blob);
		given(blob.getData()).willReturn(fileInfo);
		given(diskFs.contains(any())).willReturn(true);
		given(diskFs.contentsOf(any())).willReturn(fileContents);

		ServicesContext ctx = new ServicesContext(id, platform, state, propertySources);
		var subject = ctx.systemFilesManager();

		assertDoesNotThrow(() -> subject.loadFeeSchedules());
	}
}
