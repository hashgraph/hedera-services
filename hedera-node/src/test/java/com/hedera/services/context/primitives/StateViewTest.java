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
import com.hedera.services.context.StateChildren;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.enums.TokenSupplyType;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleDiskFs;
import com.hedera.services.state.merkle.MerkleSchedule;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.store.tokens.TokenStore;
import com.hedera.services.store.tokens.views.UniqTokenView;
import com.hedera.services.store.tokens.views.UniqTokenViewFactory;
import com.hedera.services.store.tokens.views.internals.PermHashInteger;
import com.hedera.services.store.tokens.views.internals.PermHashLong;
import com.hedera.services.utils.MiscUtils;
import com.hedera.test.extensions.LogCaptor;
import com.hedera.test.extensions.LogCaptureExtension;
import com.hedera.test.extensions.LoggingSubject;
import com.hedera.test.extensions.LoggingTarget;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hedera.test.factories.fees.CustomFeeBuilder;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CryptoGetInfoResponse;
import com.hederahashgraph.api.proto.java.CustomFee;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenKycStatus;
import com.hederahashgraph.api.proto.java.TokenNftInfo;
import com.hederahashgraph.api.proto.java.TokenRelationship;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.fchashmap.FCOneToManyRelation;
import com.swirlds.merkle.map.MerkleMap;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import static com.hedera.services.state.merkle.MerkleScheduleTest.scheduleCreateTxnWith;
import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.services.state.submerkle.RichInstant.fromJava;
import static com.hedera.services.store.tokens.TokenStore.MISSING_TOKEN;
import static com.hedera.services.store.tokens.views.EmptyUniqTokenViewFactory.EMPTY_UNIQ_TOKEN_VIEW_FACTORY;
import static com.hedera.services.store.tokens.views.EmptyUniqueTokenView.EMPTY_UNIQUE_TOKEN_VIEW;
import static com.hedera.services.utils.EntityIdUtils.asAccount;
import static com.hedera.services.utils.EntityIdUtils.asSolidityAddress;
import static com.hedera.services.utils.EntityIdUtils.asSolidityAddressHex;
import static com.hedera.services.utils.MiscUtils.asKeyUnchecked;
import static com.hedera.test.factories.fees.CustomFeeBuilder.fixedHbar;
import static com.hedera.test.factories.fees.CustomFeeBuilder.fixedHts;
import static com.hedera.test.factories.fees.CustomFeeBuilder.fractional;
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
import static com.hederahashgraph.api.proto.java.TokenType.FUNGIBLE_COMMON;
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
	private TokenID tokenId = asToken("0.0.5");
	private TokenID nftTokenId = asToken("0.0.3");
	private TokenID missingTokenId = asToken("0.0.5555");
	private AccountID payerAccountId = asAccount("0.0.9");
	private AccountID tokenAccountId = asAccount("0.0.10");
	private AccountID treasuryOwnerId = asAccount("0.0.0");
	private AccountID nftOwnerId = asAccount("0.0.44");
	private ScheduleID scheduleId = asSchedule("0.0.8");
	private ScheduleID missingScheduleId = asSchedule("0.0.9");
	private ContractID cid = asContract("0.0.1");
	private byte[] cidAddress = asSolidityAddress((int) cid.getShardNum(), cid.getRealmNum(), cid.getContractNum());
	private ContractID notCid = asContract("0.0.3");
	private AccountID autoRenew = asAccount("0.0.6");
	private AccountID creatorAccountID = asAccount("0.0.7");
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

	private MerkleMap<PermHashInteger, MerkleToken> tokens;
	private MerkleMap<PermHashInteger, MerkleTopic> topics;
	private MerkleMap<PermHashInteger, MerkleAccount> contracts;
	private MerkleMap<PermHashLong, MerkleTokenRelStatus> tokenRels;
	private FCOneToManyRelation<PermHashInteger, Long> nftsByType;
	private FCOneToManyRelation<PermHashInteger, Long> nftsByOwner;
	private FCOneToManyRelation<PermHashInteger, Long> treasuryNftsByType;
	private TokenStore tokenStore;
	private ScheduleStore scheduleStore;
	private TransactionBody parentScheduleCreate;

	private MerkleToken token;
	private MerkleSchedule schedule;
	private MerkleAccount nftOwner;
	private MerkleAccount contract;
	private MerkleAccount notContract;
	private MerkleAccount tokenAccount;
	private NodeLocalProperties nodeProps;
	private MerkleDiskFs diskFs;
	private UniqTokenView uniqTokenView;
	private UniqTokenViewFactory uniqTokenViewFactory;
	private StateChildren children;

	@LoggingTarget
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
		tokenAccount = MerkleAccountFactory.newAccount()
				.isSmartContract(false)
				.tokens(tokenId)
				.get();
		tokenAccount.setNftsOwned(10);
		tokenAccount.setMaxAutomaticAssociations(123);
		contract = MerkleAccountFactory.newAccount()
				.memo("Stay cold...")
				.isSmartContract(true)
				.accountKeys(COMPLEX_KEY_ACCOUNT_KT)
				.proxy(asAccount("0.0.3"))
				.senderThreshold(1_234L)
				.receiverThreshold(4_321L)
				.receiverSigRequired(true)
				.balance(555L)
				.autoRenewPeriod(1_000_000L)
				.deleted(true)
				.expirationTime(9_999_999L)
				.get();
		nftOwner = MerkleAccountFactory.newAccount()
				.get();
		contracts = (MerkleMap<PermHashInteger, MerkleAccount>) mock(MerkleMap.class);
		given(contracts.get(PermHashInteger.fromContractId(cid))).willReturn(contract);
		given(contracts.get(PermHashInteger.fromAccountId(nftOwnerId))).willReturn(nftOwner);
		given(contracts.get(PermHashInteger.fromContractId(notCid))).willReturn(notContract);
		given(contracts.get(PermHashInteger.fromAccountId(tokenAccountId))).willReturn(tokenAccount);

		topics = (MerkleMap<PermHashInteger, MerkleTopic>) mock(MerkleMap.class);

		tokenRels = new MerkleMap<>();
		tokenRels.put(
				PermHashLong.fromLongs(tokenAccountId.getAccountNum(), tokenId.getTokenNum()),
				new MerkleTokenRelStatus(123L, false, true, true));

		tokenStore = mock(TokenStore.class);
		token = new MerkleToken(
				Long.MAX_VALUE, 100, 1,
				"UnfrozenToken", "UnfrozenTokenName", true, true,
				new EntityId(0, 0, 3));
		token.setMemo(tokenMemo);
		token.setAdminKey(TxnHandlingScenario.TOKEN_ADMIN_KT.asJKey());
		token.setFreezeKey(TxnHandlingScenario.TOKEN_FREEZE_KT.asJKey());
		token.setKycKey(TxnHandlingScenario.TOKEN_KYC_KT.asJKey());
		token.setSupplyKey(COMPLEX_KEY_ACCOUNT_KT.asJKey());
		token.setWipeKey(MISC_ACCOUNT_KT.asJKey());
		token.setFeeScheduleKey(MISC_ACCOUNT_KT.asJKey());
		token.setAutoRenewAccount(EntityId.fromGrpcAccountId(autoRenew));
		token.setExpiry(expiry);
		token.setAutoRenewPeriod(autoRenewPeriod);
		token.setDeleted(true);
		token.setTokenType(TokenType.FUNGIBLE_COMMON);
		token.setSupplyType(TokenSupplyType.FINITE);
		token.setFeeScheduleFrom(grpcCustomFees, null);

		given(tokenStore.resolve(tokenId)).willReturn(tokenId);
		given(tokenStore.resolve(missingTokenId)).willReturn(TokenStore.MISSING_TOKEN);
		given(tokenStore.listOfTokensServed(nftOwnerId)).willReturn(
				Collections.singletonList(targetNftKey.getHiPhi().toGrpcTokenId()));
		given(tokenStore.get(tokenId)).willReturn(token);
		given(tokenStore.get(IdUtils.asToken("0.0.3"))).willReturn(token);

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

		var uniqueTokens = new MerkleMap<PermHashLong, MerkleUniqueToken>();
		uniqueTokens.put(targetNftKey, targetNft);
		uniqueTokens.put(treasuryNftKey, treasuryNft);

		nftsByOwner = (FCOneToManyRelation<PermHashInteger, Long>) mock(FCOneToManyRelation.class);
		nftsByType = (FCOneToManyRelation<PermHashInteger, Long>) mock(FCOneToManyRelation.class);
		treasuryNftsByType = (FCOneToManyRelation<PermHashInteger, Long>) mock(FCOneToManyRelation.class);
		uniqTokenView = mock(UniqTokenView.class);
		uniqTokenViewFactory = mock(UniqTokenViewFactory.class);

		children = new StateChildren();
		children.setUniqueTokens(uniqueTokens);
		children.setAccounts(contracts);
		children.setTokenAssociations(tokenRels);
		children.setUniqueTokenAssociations(nftsByType);
		children.setUniqueTokenAssociations(nftsByOwner);
		children.setUniqueOwnershipTreasuryAssociations(treasuryNftsByType);
		children.setDiskFs(diskFs);

		given(uniqTokenViewFactory.viewFor(any(), any(), any(), any(), any(), any())).willReturn(uniqTokenView);

		subject = new StateView(
				tokenStore,
				scheduleStore,
				nodeProps,
				children,
				uniqTokenViewFactory);
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
	void usesFactoryForUniqTokensView() {
		assertSame(subject.uniqTokenView(), uniqTokenView);
	}

	@Test
	void tokenExistsWorks() {
		// expect:
		assertTrue(subject.tokenExists(tokenId));
		assertFalse(subject.tokenExists(missingTokenId));
	}

	@Test
	void nftExistsWorks() {
		// expect:
		assertTrue(subject.nftExists(targetNftId));
		assertFalse(subject.nftExists(missingNftId));
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
		assertEquals(fromJava(resolutionTime).toGrpc(), info.getDeletionTime());
	}

	@Test
	void getsScheduleInfoForExecuted() {
		// when:
		schedule.markExecuted(resolutionTime);
		var gotten = subject.infoForSchedule(scheduleId);
		var info = gotten.get();

		// then:
		assertEquals(fromJava(resolutionTime).toGrpc(), info.getExecutionTime());
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
		assertEquals(token.grpcFeeSchedule(), info.getCustomFeesList());
		assertEquals(TOKEN_ADMIN_KT.asKey(), info.getAdminKey());
		assertEquals(TOKEN_FREEZE_KT.asKey(), info.getFreezeKey());
		assertEquals(TOKEN_KYC_KT.asKey(), info.getKycKey());
		assertEquals(miscKey, info.getWipeKey());
		assertEquals(miscKey, info.getFeeScheduleKey());
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
		assertEquals(JKey.mapJKey(contract.getAccountKey()), info.getAdminKey());
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
	void getTokenRelationship() {
		// given:
		given(tokenStore.exists(tokenId)).willReturn(true);
		given(tokenStore.get(tokenId)).willReturn(token);

		List<TokenRelationship> expectedRels = List.of(
				TokenRelationship.newBuilder()
						.setTokenId(tokenId)
						.setSymbol("UnfrozenToken")
						.setBalance(123L)
						.setKycStatus(TokenKycStatus.Granted)
						.setFreezeStatus(TokenFreezeStatus.Unfrozen)
						.setAutomaticAssociation(true)
						.setDecimals(1)
						.build());

		// when:
		var actualRels = StateView.tokenRels(subject, tokenAccountId);

		// then:
		assertEquals(expectedRels, actualRels);
	}

	@Test
	void getInfoForNftMissing() {
		// setup:
		var nftID = NftID.newBuilder().setTokenID(tokenId).setSerialNumber(123L).build();

		// when:
		var actualTokenNftInfo = subject.infoForNft(nftID);

		// then:
		assertEquals(Optional.empty(), actualTokenNftInfo);
	}

	@Test
	void getTokenType() {
		// when:
		var actualTokenType = subject.tokenType(tokenId).get();

		// then:
		assertEquals(FUNGIBLE_COMMON, actualTokenType);
	}

	@Test
	void getTokenTypeMissing() {
		// setup:
		given(tokenStore.resolve(tokenId)).willReturn(MISSING_TOKEN);

		// when:
		var actualTokenType = subject.tokenType(tokenId);

		// then:
		assertEquals(Optional.empty(), actualTokenType);
	}

	@Test
	void getTokenTypeException() {
		// setup:
		given(tokenStore.get(tokenId)).willThrow(new RuntimeException());

		// when:
		var actualTokenType = subject.tokenType(tokenId);

		// then:
		assertEquals(Optional.empty(), actualTokenType);
	}

	@Test
	void infoForAccount() {
		// setup:
		var expectedResponse = CryptoGetInfoResponse.AccountInfo.newBuilder()
				.setKey(asKeyUnchecked(tokenAccount.getAccountKey()))
				.setAccountID(tokenAccountId)
				.setReceiverSigRequired(tokenAccount.isReceiverSigRequired())
				.setDeleted(tokenAccount.isDeleted())
				.setMemo(tokenAccount.getMemo())
				.setAutoRenewPeriod(Duration.newBuilder().setSeconds(tokenAccount.getAutoRenewSecs()))
				.setBalance(tokenAccount.getBalance())
				.setExpirationTime(Timestamp.newBuilder().setSeconds(tokenAccount.getExpiry()))
				.setContractAccountID(asSolidityAddressHex(tokenAccountId))
				.setOwnedNfts(tokenAccount.getNftsOwned())
				.setMaxAutomaticTokenAssociations(tokenAccount.getMaxAutomaticAssociations())
				.build();

		// when:
		var actualResponse = subject.infoForAccount(tokenAccountId);

		// then:
		assertEquals(expectedResponse, actualResponse.get());
	}

	@Test
	void numNftsOwnedWorksForExisting() {
		// expect:
		assertEquals(tokenAccount.getNftsOwned(), subject.numNftsOwnedBy(tokenAccountId));
	}

	@Test
	void infoForAccountEmpty() {
		// setup:
		given(contracts.get(PermHashInteger.fromAccountId(tokenAccountId))).willReturn(null);

		// when:
		var actualResponse = subject.infoForAccount(tokenAccountId);

		// then:
		assertEquals(Optional.empty(), actualResponse);
	}

	@Test
	void getTopics() {
		// setup:
		final var children = new StateChildren();
		children.setTopics(topics);

		subject = new StateView(
				null, null, null, children, EMPTY_UNIQ_TOKEN_VIEW_FACTORY);

		// when:
		var actualTopics = subject.topics();

		// then:
		assertEquals(topics, actualTopics);
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
		contract.setAccountKey(null);

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

	@Test
	void rejectsMissingNft() {
		// when:
		final var optionalNftInfo = subject.infoForNft(missingNftId);

		// then:
		assertTrue(optionalNftInfo.isEmpty());
	}

	@Test
	void abortsNftGetWhenMissingTreasuryAsExpected() {
		// setup:
		tokens = mock(MerkleMap.class);
		children.setTokens(tokens);
		// and:
		targetNft.setOwner(MISSING_ENTITY_ID);

		// when:
		final var optionalNftInfo = subject.infoForNft(targetNftId);

		// then:
		assertTrue(optionalNftInfo.isEmpty());
	}

	@Test
	void interpolatesTreasuryIdOnNftGet() {
		// setup:
		tokens = mock(MerkleMap.class);
		children.setTokens(tokens);
		// and:
		targetNft.setOwner(MISSING_ENTITY_ID);

		final var token = new MerkleToken();
		token.setTreasury(EntityId.fromGrpcAccountId(tokenAccountId));
		given(tokens.get(targetNftKey.getHiPhi())).willReturn(token);

		// when:
		final var optionalNftInfo = subject.infoForNft(targetNftId);

		// then:
		final var info = optionalNftInfo.get();
		assertEquals(targetNftId, info.getNftID());
		assertEquals(tokenAccountId, info.getAccountID());
		assertEquals(fromJava(nftCreation).toGrpc(), info.getCreationTime());
		assertArrayEquals(nftMeta, info.getMetadata().toByteArray());
	}

	@Test
	void getNftsAsExpected() {
		// when:
		final var optionalNftInfo = subject.infoForNft(targetNftId);

		// then:
		assertTrue(optionalNftInfo.isPresent());
		// and:
		final var info = optionalNftInfo.get();
		assertEquals(targetNftId, info.getNftID());
		assertEquals(nftOwnerId, info.getAccountID());
		assertEquals(fromJava(nftCreation).toGrpc(), info.getCreationTime());
		assertArrayEquals(nftMeta, info.getMetadata().toByteArray());
	}

	@Test
	void infoForAccountNftsWorks() {
		// setup:
		final List<TokenNftInfo> mockInfo = new ArrayList<>();

		given(uniqTokenView.ownedAssociations(tokenAccountId, 3L, 4L)).willReturn(mockInfo);

		// when:
		var result = subject.infoForAccountNfts(tokenAccountId, 3L, 4L);

		assertFalse(result.isEmpty());
		assertSame(mockInfo, result.get());
	}

	@Test
	void infoForMissingAccountNftsReturnsEmpty() {
		var result = subject.infoForAccountNfts(creatorAccountID, 0, 1);
		assertTrue(result.isEmpty());
	}

	@Test
	void infoForTokenNftsWorks() {
		// setup:
		final List<TokenNftInfo> mockInfo = new ArrayList<>();

		given(uniqTokenView.typedAssociations(nftTokenId, 3L, 4L)).willReturn(mockInfo);

		// when:
		var result = subject.infosForTokenNfts(nftTokenId, 3L, 4L);

		assertFalse(result.isEmpty());
		assertSame(mockInfo, result.get());
	}

	@Test
	void infoForMissingTokenNftsReturnsEmpty() {
		var result = subject.infosForTokenNfts(missingTokenId, 0, 1);
		assertTrue(result.isEmpty());
	}

	@Test
	void viewAdaptToNullChildren() {
		// given:
		subject = new StateView(null, null, null, null, EMPTY_UNIQ_TOKEN_VIEW_FACTORY);

		// expect:
		assertSame(EMPTY_UNIQUE_TOKEN_VIEW, subject.uniqTokenView());
		// and:
		assertSame(StateView.EMPTY_FCOTMR, subject.treasuryNftsByType());
		assertSame(StateView.EMPTY_FCOTMR, subject.nftsByOwner());
		assertSame(StateView.EMPTY_FCOTMR, subject.nftsByType());
		// and:
		assertSame(StateView.EMPTY_FCM, subject.tokens());
		assertSame(StateView.EMPTY_FCM, subject.storage());
		assertSame(StateView.EMPTY_FCM, subject.uniqueTokens());
		assertSame(StateView.EMPTY_FCM, subject.tokenAssociations());
		assertSame(StateView.EMPTY_FCM, subject.contracts());
		assertSame(StateView.EMPTY_FCM, subject.accounts());
		assertSame(StateView.EMPTY_FCM, subject.topics());
		// and:
		assertTrue(subject.contentsOf(target).isEmpty());
		assertTrue(subject.infoForFile(target).isEmpty());
		assertTrue(subject.infoForContract(cid).isEmpty());
		assertTrue(subject.infoForAccount(tokenAccountId).isEmpty());
		assertTrue(subject.infoForAccountNfts(nftOwnerId, 0, Long.MAX_VALUE).isEmpty());
		assertTrue(subject.infosForTokenNfts(nftTokenId, 0, Long.MAX_VALUE).isEmpty());
		assertTrue(subject.tokenType(tokenId).isEmpty());
		assertTrue(subject.infoForNft(targetNftId).isEmpty());
		assertTrue(subject.infoForSchedule(scheduleId).isEmpty());
		assertTrue(subject.infoForToken(tokenId).isEmpty());
		assertTrue(subject.tokenWith(tokenId).isEmpty());
		assertFalse(subject.nftExists(targetNftId));
		assertFalse(subject.scheduleExists(scheduleId));
		assertFalse(subject.tokenExists(tokenId));
		// and:
		assertEquals(0, subject.numNftsOwnedBy(nftOwnerId));
	}

	private final Instant nftCreation = Instant.ofEpochSecond(1_234_567L, 8);
	private final byte[] nftMeta = "abcdefgh".getBytes();
	private final NftID targetNftId = NftID.newBuilder()
			.setTokenID(IdUtils.asToken("0.0.3"))
			.setSerialNumber(4L)
			.build();
	private final NftID missingNftId = NftID.newBuilder()
			.setTokenID(IdUtils.asToken("0.0.9"))
			.setSerialNumber(5L)
			.build();
	private final PermHashLong targetNftKey = PermHashLong.fromLongs(3, 4);
	private final PermHashLong treasuryNftKey = PermHashLong.fromLongs(3, 5);
	private final MerkleUniqueToken targetNft = new MerkleUniqueToken(EntityId.fromGrpcAccountId(nftOwnerId), nftMeta,
			fromJava(nftCreation));
	private final MerkleUniqueToken treasuryNft = new MerkleUniqueToken(EntityId.fromGrpcAccountId(treasuryOwnerId),
			nftMeta,
			fromJava(nftCreation));

	private CustomFeeBuilder builder = new CustomFeeBuilder(payerAccountId);
	private CustomFee customFixedFeeInHbar = builder.withFixedFee(fixedHbar(100L));
	private CustomFee customFixedFeeInHts = builder.withFixedFee(fixedHts(tokenId, 100L));
	private CustomFee customFixedFeeSameToken = builder.withFixedFee(fixedHts(50L));
	private CustomFee customFractionalFee = builder.withFractionalFee(
			fractional(15L, 100L)
					.setMinimumAmount(10L)
					.setMaximumAmount(50L));
	private List<CustomFee> grpcCustomFees = List.of(
			customFixedFeeInHbar,
			customFixedFeeInHts,
			customFixedFeeSameToken,
			customFractionalFee
	);
}
