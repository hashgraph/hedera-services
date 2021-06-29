package com.hedera.services.context.primitives;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.google.protobuf.ByteString;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleDiskFs;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.utils.MiscUtils;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Fraction;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenKycStatus;
import com.hederahashgraph.api.proto.java.TokenRelationship;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.fcmap.FCMap;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import proto.CustomFeesOuterClass;

import javax.inject.Inject;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static com.hedera.services.state.merkle.MerkleScheduleTest.scheduleCreateTxnWith;
import static com.hedera.services.utils.EntityIdUtils.asAccount;
import static com.hedera.services.utils.EntityIdUtils.asSolidityAddress;
import static com.hedera.services.utils.EntityIdUtils.asSolidityAddressHex;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.SCHEDULE_ADMIN_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_ADMIN_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_FREEZE_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_KYC_KT;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asContract;
import static com.hedera.test.utils.IdUtils.asFile;
import static com.hedera.test.utils.IdUtils.asSchedule;
import static com.hedera.test.utils.IdUtils.asToken;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

@ExtendWith(LogCaptureExtension.class)
class StateViewTest {
	private Instant resolutionTime = Instant.ofEpochSecond(123L);
	private RichInstant now = RichInstant.fromGrpc(Timestamp.newBuilder().setNanos(123123213).build());
	private long expiry = 2_000_000L;
	private byte[] data = "SOMETHING".getBytes();
	private byte[] expectedBytecode = "A Supermarket in California".getBytes();
	private byte[] expectedStorage = "The Ecstasy".getBytes();
	private String tokenMemo = "Goodbye and keep cold";
	private HFileMeta metadata;
	private HFileMeta immutableMetadata;
	private FileID target = asFile("0.0.123");
	private TokenID tokenId = asToken("2.4.5");
	private TokenID missingTokenId = asToken("3.4.5");
	private AccountID payerAccountId = asAccount("9.9.9");
	private ScheduleID scheduleId = asSchedule("6.7.8");
	private ScheduleID missingScheduleId = asSchedule("7.8.9");
	private ContractID cid = asContract("3.2.1");
	private byte[] cidAddress = asSolidityAddress((int) cid.getShardNum(), cid.getRealmNum(), cid.getContractNum());
	private ContractID notCid = asContract("1.2.3");
	private AccountID autoRenew = asAccount("2.4.6");
	private AccountID creatorAccountID = asAccount("3.5.7");
	private long autoRenewPeriod = 1_234_567;
	private String fileMemo = "Originally she thought";
	private String scheduleMemo = "For what but eye and ear";

	private FileGetInfoResponse.FileInfo expected;
	private FileGetInfoResponse.FileInfo expectedImmutable;

	private Map<byte[], byte[]> storage;
	private Map<byte[], byte[]> bytecode;
	private Map<FileID, byte[]> contents;
	private Map<FileID, HFileMeta> attrs;
	private BiFunction<StateView, AccountID, List<TokenRelationship>> mockTokenRelsFn;

	private FCMap<MerkleEntityId, MerkleAccount> contracts;
	private TokenStore tokenStore;
	private ScheduleStore scheduleStore;
	private TransactionBody parentScheduleCreate;

	private MerkleToken token;
	private MerkleSchedule schedule;
	private MerkleAccount contract;
	private MerkleAccount notContract;
	private NodeLocalProperties nodeProps;
	private MerkleDiskFs diskFs;

	@Inject
	private LogCaptor logCaptor;

	@LoggingSubject
	private StateView subject;

	@BeforeEach
	private void setup() throws Throwable {
		metadata = new HFileMeta(
				false,
				TxnHandlingScenario.MISC_FILE_WACL_KT.asJKey(),
				expiry,
				fileMemo);
		immutableMetadata = new HFileMeta(
				false,
				StateView.EMPTY_WACL,
				expiry);

		expectedImmutable = FileGetInfoResponse.FileInfo.newBuilder()
				.setDeleted(false)
				.setExpirationTime(Timestamp.newBuilder().setSeconds(expiry))
				.setFileID(target)
				.setSize(data.length)
				.build();
		expected = expectedImmutable.toBuilder()
				.setKeys(TxnHandlingScenario.MISC_FILE_WACL_KT.asKey().getKeyList())
				.setMemo(fileMemo)
				.build();

		notContract = MerkleAccountFactory.newAccount()
				.isSmartContract(false)
				.get();
		contract = MerkleAccountFactory.newAccount()
				.memo("Stay cold...")
				.isSmartContract(true)
				.accountKeys(COMPLEX_KEY_ACCOUNT_KT)
				.proxy(asAccount("1.2.3"))
				.senderThreshold(1_234L)
				.receiverThreshold(4_321L)
				.receiverSigRequired(true)
				.balance(555L)
				.autoRenewPeriod(1_000_000L)
				.deleted(true)
				.expirationTime(9_999_999L)
				.get();
		contracts = (FCMap<MerkleEntityId, MerkleAccount>) mock(FCMap.class);
		given(contracts.get(MerkleEntityId.fromContractId(cid))).willReturn(contract);
		given(contracts.get(MerkleEntityId.fromContractId(notCid))).willReturn(notContract);

		tokenStore = mock(TokenStore.class);
		token = new MerkleToken(
				Long.MAX_VALUE, 100, 1,
				"UnfrozenToken", "UnfrozenTokenName", true, true,
				new EntityId(1, 2, 3));
		token.setMemo(tokenMemo);
		token.setAdminKey(TxnHandlingScenario.TOKEN_ADMIN_KT.asJKey());
		token.setFreezeKey(TxnHandlingScenario.TOKEN_FREEZE_KT.asJKey());
		token.setKycKey(TxnHandlingScenario.TOKEN_KYC_KT.asJKey());
		token.setSupplyKey(COMPLEX_KEY_ACCOUNT_KT.asJKey());
		token.setWipeKey(MISC_ACCOUNT_KT.asJKey());
		token.setAutoRenewAccount(EntityId.fromGrpcAccountId(autoRenew));
		token.setExpiry(expiry);
		token.setAutoRenewPeriod(autoRenewPeriod);
		token.setDeleted(true);
		token.setFeeScheduleFrom(grpcCustomFees);
		given(tokenStore.resolve(tokenId)).willReturn(tokenId);
		given(tokenStore.resolve(missingTokenId)).willReturn(TokenStore.MISSING_TOKEN);
		given(tokenStore.get(tokenId)).willReturn(token);

		scheduleStore = mock(ScheduleStore.class);
		parentScheduleCreate =
				scheduleCreateTxnWith(
						SCHEDULE_ADMIN_KT.asKey(),
						scheduleMemo,
						payerAccountId,
						creatorAccountID,
						MiscUtils.asTimestamp(now.toJava())
				);
		schedule = MerkleSchedule.from(parentScheduleCreate.toByteArray(), expiry);
		schedule.witnessValidEd25519Signature("firstPretendKey".getBytes());
		schedule.witnessValidEd25519Signature("secondPretendKey".getBytes());
		schedule.witnessValidEd25519Signature("thirdPretendKey".getBytes());

		given(scheduleStore.resolve(scheduleId)).willReturn(scheduleId);
		given(scheduleStore.resolve(missingScheduleId)).willReturn(ScheduleStore.MISSING_SCHEDULE);
		given(scheduleStore.get(scheduleId)).willReturn(schedule);

		contents = mock(Map.class);
		attrs = mock(Map.class);
		storage = mock(Map.class);
		bytecode = mock(Map.class);
		given(storage.get(argThat((byte[] bytes) -> Arrays.equals(cidAddress, bytes)))).willReturn(expectedStorage);
		given(bytecode.get(argThat((byte[] bytes) -> Arrays.equals(cidAddress, bytes)))).willReturn(expectedBytecode);
		nodeProps = mock(NodeLocalProperties.class);
		diskFs = mock(MerkleDiskFs.class);

		mockTokenRelsFn = (BiFunction<StateView, AccountID, List<TokenRelationship>>) mock(BiFunction.class);

		StateView.tokenRelsFn = mockTokenRelsFn;
		given(mockTokenRelsFn.apply(any(), any())).willReturn(Collections.emptyList());

		subject = new StateView(
				tokenStore,
				scheduleStore,
				StateView.EMPTY_TOPICS_SUPPLIER,
				() -> contracts,
				nodeProps,
				() -> diskFs);
		subject.fileAttrs = attrs;
		subject.fileContents = contents;
		subject.contractBytecode = bytecode;
		subject.contractStorage = storage;
	}

	@AfterEach
	void cleanup() {
		StateView.tokenRelsFn = StateView::tokenRels;
	}

	@Test
	void tokenExistsWorks() {
		// expect:
		assertTrue(subject.tokenExists(tokenId));
		assertFalse(subject.tokenExists(missingTokenId));
	}

	@Test
	void scheduleExistsWorks() {
		// expect:
		assertTrue(subject.scheduleExists(scheduleId));
		assertFalse(subject.scheduleExists(missingScheduleId));
	}

	@Test
	void tokenWithWorks() {
		given(tokenStore.exists(tokenId)).willReturn(true);
		given(tokenStore.get(tokenId)).willReturn(token);

		// expect:
		assertSame(token, subject.tokenWith(tokenId).get());
	}

	@Test
	void tokenWithWorksForMissing() {
		given(tokenStore.exists(tokenId)).willReturn(false);

		// expect:
		assertTrue(subject.tokenWith(tokenId).isEmpty());
	}

	@Test
	void recognizesMissingSchedule() {
		// when:
		var info = subject.infoForSchedule(missingScheduleId);

		// then:
		assertTrue(info.isEmpty());
	}

	@Test
	void infoForScheduleFailsGracefully() {
		given(scheduleStore.get(any())).willThrow(IllegalArgumentException.class);

		// when:
		var info = subject.infoForSchedule(scheduleId);

		// then:
		assertTrue(info.isEmpty());
	}

	@Test
	void getsScheduleInfoForDeleted() {
		// setup:
		var expectedScheduledTxn = parentScheduleCreate.getScheduleCreate().getScheduledTransactionBody();

		// when:
		schedule.markDeleted(resolutionTime);
		var gotten = subject.infoForSchedule(scheduleId);
		var info = gotten.get();

		// then:
		assertEquals(scheduleId, info.getScheduleID());
		assertEquals(schedule.schedulingAccount().toGrpcAccountId(), info.getCreatorAccountID());
		assertEquals(schedule.payer().toGrpcAccountId(), info.getPayerAccountID());
		assertEquals(Timestamp.newBuilder().setSeconds(expiry).build(), info.getExpirationTime());
		var expectedSignatoryList = KeyList.newBuilder();
		schedule.signatories()
				.forEach(a -> expectedSignatoryList.addKeys(Key.newBuilder().setEd25519(ByteString.copyFrom(a))));
		assertArrayEquals(
				expectedSignatoryList.build().getKeysList().toArray(),
				info.getSigners().getKeysList().toArray());
		assertEquals(SCHEDULE_ADMIN_KT.asKey(), info.getAdminKey());
		assertEquals(expectedScheduledTxn, info.getScheduledTransactionBody());
		assertEquals(schedule.scheduledTransactionId(), info.getScheduledTransactionID());
		assertEquals(RichInstant.fromJava(resolutionTime).toGrpc(), info.getDeletionTime());
	}

	@Test
	void getsScheduleInfoForExecuted() {
		// when:
		schedule.markExecuted(resolutionTime);
		var gotten = subject.infoForSchedule(scheduleId);
		var info = gotten.get();

		// then:
		assertEquals(RichInstant.fromJava(resolutionTime).toGrpc(), info.getExecutionTime());
	}

	@Test
	void recognizesMissingToken() {
		// when:
		var info = subject.infoForToken(missingTokenId);

		// then:
		assertTrue(info.isEmpty());
	}

	@Test
	void infoForTokenFailsGracefully() {
		given(tokenStore.get(any())).willThrow(IllegalArgumentException.class);

		// when:
		var info = subject.infoForToken(tokenId);

		// then:
		assertTrue(info.isEmpty());
	}

	@Test
	void getsTokenInfoMinusFreezeIfMissing() {
		// setup:
		token.setFreezeKey(MerkleToken.UNUSED_KEY);

		// when:
		var info = subject.infoForToken(tokenId).get();

		// then:
		assertEquals(tokenId, info.getTokenId());
		assertEquals(token.symbol(), info.getSymbol());
		assertEquals(token.name(), info.getName());
		assertEquals(token.treasury().toGrpcAccountId(), info.getTreasury());
		assertEquals(token.totalSupply(), info.getTotalSupply());
		assertEquals(token.decimals(), info.getDecimals());
		assertEquals(TOKEN_ADMIN_KT.asKey(), info.getAdminKey());
		assertEquals(TokenFreezeStatus.FreezeNotApplicable, info.getDefaultFreezeStatus());
		assertFalse(info.hasFreezeKey());
	}

	@Test
	void getsTokenInfo() {
		// setup:
		final var miscKey = MISC_ACCOUNT_KT.asKey();
		// when:
		var info = subject.infoForToken(tokenId).get();

		// then:
		assertTrue(info.getDeleted());
		assertEquals(token.memo(), info.getMemo());
		assertEquals(tokenId, info.getTokenId());
		assertEquals(token.symbol(), info.getSymbol());
		assertEquals(token.name(), info.getName());
		assertEquals(token.treasury().toGrpcAccountId(), info.getTreasury());
		assertEquals(token.totalSupply(), info.getTotalSupply());
		assertEquals(token.decimals(), info.getDecimals());
		assertEquals(token.grpcFeeSchedule(), info.getCustomFees());
		assertEquals(TOKEN_ADMIN_KT.asKey(), info.getAdminKey());
		assertEquals(TOKEN_FREEZE_KT.asKey(), info.getFreezeKey());
		assertEquals(TOKEN_KYC_KT.asKey(), info.getKycKey());
		assertEquals(miscKey, info.getWipeKey());
		assertEquals(autoRenew, info.getAutoRenewAccount());
		assertEquals(Duration.newBuilder().setSeconds(autoRenewPeriod).build(), info.getAutoRenewPeriod());
		assertEquals(Timestamp.newBuilder().setSeconds(expiry).build(), info.getExpiry());
		assertEquals(TokenFreezeStatus.Frozen, info.getDefaultFreezeStatus());
		assertEquals(TokenKycStatus.Granted, info.getDefaultKycStatus());
	}

	@Test
	void getsContractInfo() throws Exception {
		// setup:
		List<TokenRelationship> rels = List.of(
				TokenRelationship.newBuilder()
						.setTokenId(TokenID.newBuilder().setTokenNum(123L))
						.setFreezeStatus(TokenFreezeStatus.FreezeNotApplicable)
						.setKycStatus(TokenKycStatus.KycNotApplicable)
						.setBalance(321L)
						.build());
		given(mockTokenRelsFn.apply(subject, asAccount(cid))).willReturn(rels);

		// when:
		var info = subject.infoForContract(cid).get();

		// then:
		assertEquals(cid, info.getContractID());
		assertEquals(asAccount(cid), info.getAccountID());
		assertEquals(JKey.mapJKey(contract.getKey()), info.getAdminKey());
		assertEquals(contract.getMemo(), info.getMemo());
		assertEquals(contract.getAutoRenewSecs(), info.getAutoRenewPeriod().getSeconds());
		assertEquals(contract.getBalance(), info.getBalance());
		assertEquals(asSolidityAddressHex(asAccount(cid)), info.getContractAccountID());
		assertEquals(contract.getExpiry(), info.getExpirationTime().getSeconds());
		assertEquals(rels, info.getTokenRelationshipsList());
		assertTrue(info.getDeleted());
		// and:
		assertEquals(expectedStorage.length + expectedBytecode.length, info.getStorage());
	}

	@Test
	void returnsEmptyOptionalIfContractMissing() {
		given(contracts.get(any())).willReturn(null);

		// expect:
		assertTrue(subject.infoForContract(cid).isEmpty());
	}

	@Test
	void handlesNullKey() {
		// given:
		contract.setKey(null);

		// when:
		var info = subject.infoForContract(cid).get();

		// then:
		assertFalse(info.hasAdminKey());
	}

	@Test
	void getsAttrs() {
		given(attrs.get(target)).willReturn(metadata);

		// when
		var stuff = subject.attrOf(target);

		// then:
		assertEquals(metadata.toString(), stuff.get().toString());
	}

	@Test
	void getsBytecode() {
		// when:
		var actual = subject.bytecodeOf(cid);

		// then:
		assertArrayEquals(expectedBytecode, actual.get());
	}

	@Test
	void getsStorage() {
		// when:
		var actual = subject.storageOf(cid);

		// then:
		assertArrayEquals(expectedStorage, actual.get());
	}

	@Test
	void getsContents() {
		given(contents.get(target)).willReturn(data);

		// when
		var stuff = subject.contentsOf(target);

		// then:
		assertTrue(Arrays.equals(data, stuff.get()));
	}

	@Test
	void assemblesFileInfo() {
		given(attrs.get(target)).willReturn(metadata);
		given(contents.get(target)).willReturn(data);

		// when:
		var info = subject.infoForFile(target);

		// then:
		assertTrue(info.isPresent());
		assertEquals(expected, info.get());
	}

	@Test
	void returnFileInfoForBinaryObjectNotFoundExceptionAfterRetries() {
		// setup:
		given(attrs.get(target))
				.willThrow(new com.swirlds.blob.BinaryObjectNotFoundException())
				.willThrow(new com.swirlds.blob.BinaryObjectNotFoundException())
				.willReturn(metadata);
		given(nodeProps.queryBlobLookupRetries()).willReturn(2);
		given(contents.get(target)).willReturn(data);

		// when:
		var info = subject.infoForFile(target);

		// then:
		assertTrue(info.isPresent());
		assertEquals(expected, info.get());
	}


	@Test
	void assemblesFileInfoForImmutable() {
		given(attrs.get(target)).willReturn(immutableMetadata);
		given(contents.get(target)).willReturn(data);

		// when:
		var info = subject.infoForFile(target);

		// then:
		assertTrue(info.isPresent());
		assertEquals(expectedImmutable, info.get());
	}

	@Test
	void assemblesFileInfoForDeleted() {
		// setup:
		expected = expected.toBuilder()
				.setDeleted(true)
				.setSize(0)
				.build();
		metadata.setDeleted(true);

		given(attrs.get(target)).willReturn(metadata);

		// when:
		var info = subject.infoForFile(target);

		// then:
		assertTrue(info.isPresent());
		assertEquals(expected, info.get());
	}

	@Test
	void returnEmptyFileInfoForBinaryObjectNotFoundException() {
		// setup:
		given(attrs.get(target)).willThrow(new com.swirlds.blob.BinaryObjectNotFoundException());
		given(nodeProps.queryBlobLookupRetries()).willReturn(1);

		// when:
		var info = subject.infoForFile(target);

		// then:
		assertTrue(info.isEmpty());
	}

	@Test
	void returnEmptyFileInfoForBinaryObjectDeletedExceptionAfterRetries() {
		// setup:
		given(attrs.get(target))
				.willThrow(new com.swirlds.blob.BinaryObjectDeletedException())
				.willThrow(new com.swirlds.blob.BinaryObjectDeletedException())
				.willThrow(new com.swirlds.blob.BinaryObjectDeletedException())
				.willReturn(metadata);
		given(nodeProps.queryBlobLookupRetries()).willReturn(2);

		// when:
		var info = subject.infoForFile(target);

		// then:
		assertTrue(info.isEmpty());
	}

	@Test
	void returnFileInfoForBinaryObjectDeletedExceptionAfterRetries() {
		// setup:
		expected = expected.toBuilder()
				.setDeleted(true)
				.setSize(0)
				.build();
		metadata.setDeleted(true);

		given(attrs.get(target))
				.willThrow(new com.swirlds.blob.BinaryObjectDeletedException())
				.willThrow(new com.swirlds.blob.BinaryObjectDeletedException())
				.willReturn(metadata);
		given(nodeProps.queryBlobLookupRetries()).willReturn(2);

		// when:
		var info = subject.infoForFile(target);

		// then:
		assertTrue(info.isPresent());
		assertEquals(expected, info.get());
	}


	@Test
	void returnEmptyFileForOtherBinaryObjectException() {
		// setup:
		given(attrs.get(target)).willThrow(new com.swirlds.blob.BinaryObjectException());

		// when:
		var info = subject.infoForFile(target);

		// then:
		assertTrue(info.isEmpty());
		final var warnLogs = logCaptor.warnLogs();
		assertEquals(1, warnLogs.size());
		assertThat(warnLogs.get(0), Matchers.startsWith("Unexpected error occurred when getting info for file"));
	}


	@Test
	void logsAtDebugWhenInterrupted() throws InterruptedException {
		// setup:
		final var finalAnswer = new AtomicReference<Optional<FileGetInfoResponse.FileInfo>>();

		given(attrs.get(target)).willThrow(new com.swirlds.blob.BinaryObjectNotFoundException());
		given(nodeProps.queryBlobLookupRetries()).willReturn(5);

		// when:
		final var t = new Thread(() -> finalAnswer.set(subject.infoForFile(target)));
		// and:
		t.start();
		// and:
		for (int i = 0; i < 5; i++) {
			t.interrupt();
		}
		t.join();

		// then:
		final var debugLogs = logCaptor.debugLogs();
		assertTrue(finalAnswer.get().isEmpty());
		assertTrue(debugLogs.size() >= 2);
		assertThat(debugLogs.get(0), Matchers.startsWith("Retrying fetch of 0.0.123 file meta"));
		assertThat(debugLogs.get(1), Matchers.startsWith("Interrupted fetching meta for file 0.0.123"));
	}

	@Test
	void returnsEmptyForMissing() {
		// when:
		var info = subject.infoForFile(target);

		// then:
		assertTrue(info.isEmpty());
	}

	@Test
	void returnsEmptyForMissingContent() {
		// when:
		var info = subject.contentsOf(target);

		// then:
		assertTrue(info.isEmpty());
	}

	@Test
	void returnsEmptyForMissingAttr() {
		// when:
		var info = subject.attrOf(target);

		// then:
		assertTrue(info.isEmpty());
	}

	@Test
	void getsSpecialFileContents() {
		FileID file150 = asFile("0.0.150");

		given(diskFs.contentsOf(file150)).willReturn(data);
		given(diskFs.contains(file150)).willReturn(true);

		// when
		var stuff = subject.contentsOf(file150);

		// then:
		assertTrue(Arrays.equals(data, stuff.get()));
	}

	private CustomFeesOuterClass.FixedFee fixedFeeInTokenUnits = CustomFeesOuterClass.FixedFee.newBuilder()
			.setDenominatingTokenId(tokenId)
			.setAmount(100)
			.build();
	private CustomFeesOuterClass.FixedFee fixedFeeInHbar = CustomFeesOuterClass.FixedFee.newBuilder()
			.setAmount(100)
			.build();
	private Fraction fraction = Fraction.newBuilder().setNumerator(15).setDenominator(100).build();
	private CustomFeesOuterClass.FractionalFee fractionalFee = CustomFeesOuterClass.FractionalFee.newBuilder()
			.setFractionalAmount(fraction)
			.setMaximumAmount(50)
			.setMinimumAmount(10)
			.build();
	private CustomFeesOuterClass.CustomFee customFixedFeeInHbar = CustomFeesOuterClass.CustomFee.newBuilder()
			.setFeeCollectorAccountId(payerAccountId)
			.setFixedFee(fixedFeeInHbar)
			.build();
	private CustomFeesOuterClass.CustomFee customFixedFeeInHts = CustomFeesOuterClass.CustomFee.newBuilder()
			.setFeeCollectorAccountId(payerAccountId)
			.setFixedFee(fixedFeeInTokenUnits)
			.build();
	private CustomFeesOuterClass.CustomFee customFractionalFee = CustomFeesOuterClass.CustomFee.newBuilder()
			.setFeeCollectorAccountId(payerAccountId)
			.setFractionalFee(fractionalFee)
			.build();
	private CustomFeesOuterClass.CustomFees grpcCustomFees = CustomFeesOuterClass.CustomFees.newBuilder()
			.addCustomFees(customFixedFeeInHbar)
			.addCustomFees(customFixedFeeInHts)
			.addCustomFees(customFractionalFee)
			.build();
}
