package com.hedera.services.context.primitives;

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

import static com.hedera.services.utils.EntityIdUtils.asAccount;
import static com.hedera.services.utils.EntityIdUtils.asSolidityAddress;
import static com.hedera.services.utils.EntityIdUtils.asSolidityAddressHex;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.MISC_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_ADMIN_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_FREEZE_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_KYC_KT;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asContract;
import static com.hedera.test.utils.IdUtils.asFile;
import static com.hedera.test.utils.IdUtils.asSchedule;
import static com.hedera.test.utils.IdUtils.asToken;
import static org.junit.jupiter.api.Assertions.*;

import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.state.merkle.MerkleDiskFs;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.tokens.TokenStore;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hedera.services.legacy.core.jproto.JFileInfo;
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenKycStatus;
import com.hederahashgraph.api.proto.java.TokenRelationship;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import javax.swing.plaf.nimbus.State;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.*;

@RunWith(JUnitPlatform.class)
class StateViewTest {
	long expiry = 2_000_000L;
	byte[] data = "SOMETHING".getBytes();
	byte[] expectedBytecode = "A Supermarket in California".getBytes();
	byte[] expectedStorage = "The Ecstasy".getBytes();
	JFileInfo metadata;
	JFileInfo immutableMetadata;
	FileID target = asFile("0.0.123");
	TokenID tokenId = asToken("2.4.5");
	TokenID missingTokenId = asToken("3.4.5");
	ScheduleID scheduleId = asSchedule("6.7.8");
	ContractID cid = asContract("3.2.1");
	byte[] cidAddress = asSolidityAddress((int) cid.getShardNum(), cid.getRealmNum(), cid.getContractNum());
	ContractID notCid = asContract("1.2.3");
	AccountID autoRenew = asAccount("2.4.6");
	long autoRenewPeriod = 1_234_567;

	FileGetInfoResponse.FileInfo expected;
	FileGetInfoResponse.FileInfo expectedImmutable;

	Map<byte[], byte[]> storage;
	Map<byte[], byte[]> bytecode;
	Map<FileID, byte[]> contents;
	Map<FileID, JFileInfo> attrs;
	BiFunction<StateView, AccountID, List<TokenRelationship>> mockTokenRelsFn;

	FCMap<MerkleEntityId, MerkleAccount> contracts;
	TokenStore tokenStore;

	MerkleToken token;
	MerkleAccount contract;
	MerkleAccount notContract;
	PropertySource propertySource;
	MerkleDiskFs diskFs;

	StateView subject;

	@BeforeEach
	private void setup() throws Throwable {
		metadata = new JFileInfo(
				false,
				TxnHandlingScenario.MISC_FILE_WACL_KT.asJKey(),
				expiry);
		immutableMetadata = new JFileInfo(
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
		token.setAdminKey(TxnHandlingScenario.TOKEN_ADMIN_KT.asJKey());
		token.setFreezeKey(TxnHandlingScenario.TOKEN_FREEZE_KT.asJKey());
		token.setKycKey(TxnHandlingScenario.TOKEN_KYC_KT.asJKey());
		token.setSupplyKey(COMPLEX_KEY_ACCOUNT_KT.asJKey());
		token.setWipeKey(MISC_ACCOUNT_KT.asJKey());
		token.setAutoRenewAccount(EntityId.ofNullableAccountId(autoRenew));
		token.setExpiry(expiry);
		token.setAutoRenewPeriod(autoRenewPeriod);
		token.setDeleted(true);
		given(tokenStore.resolve(tokenId)).willReturn(tokenId);
		given(tokenStore.resolve(missingTokenId)).willReturn(TokenStore.MISSING_TOKEN);
		given(tokenStore.get(tokenId)).willReturn(token);

		contents = mock(Map.class);
		attrs = mock(Map.class);
		storage = mock(Map.class);
		bytecode = mock(Map.class);
		given(storage.get(argThat((byte[] bytes) -> Arrays.equals(cidAddress, bytes)))).willReturn(expectedStorage);
		given(bytecode.get(argThat((byte[] bytes) -> Arrays.equals(cidAddress, bytes)))).willReturn(expectedBytecode);
		propertySource = mock(PropertySource.class);
		diskFs = mock(MerkleDiskFs.class);

		mockTokenRelsFn = (BiFunction<StateView, AccountID, List<TokenRelationship>>) mock(BiFunction.class);

		StateView.tokenRelsFn = mockTokenRelsFn;
		given(mockTokenRelsFn.apply(any(), any())).willReturn(Collections.emptyList());

		subject = new StateView(
				tokenStore,
				StateView.EMPTY_TOPICS_SUPPLIER,
				() -> contracts,
				propertySource,
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
	public void tokenExistsWorks() {
		// expect:
		assertTrue(subject.tokenExists(tokenId));
		assertFalse(subject.tokenExists(missingTokenId));
	}

	@Test
	public void tokenWithWorks() {
		given(tokenStore.exists(tokenId)).willReturn(true);
		given(tokenStore.get(tokenId)).willReturn(token);

		// expect:
		assertSame(token, subject.tokenWith(tokenId).get());
	}

	@Test
	public void tokenWithWorksForMissing() {
		given(tokenStore.exists(tokenId)).willReturn(false);

		// expect:
		assertTrue(subject.tokenWith(tokenId).isEmpty());
	}

	@Test
	public void recognizesMissingToken() {
		// when:
		var info = subject.infoForToken(missingTokenId);

		// then:
		assertTrue(info.isEmpty());
	}

	@Test
	public void failsGracefully() {
		given(tokenStore.get(any())).willThrow(IllegalArgumentException.class);

		// when:
		var info = subject.infoForToken(tokenId);

		// then:
		assertTrue(info.isEmpty());
	}

	@Test
	public void getsTokenInfoMinusFreezeIfMissing() {
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
	public void getsTokenInfo() {
		// when:
		var info = subject.infoForToken(tokenId).get();

		// then:
		assertTrue(info.getDeleted());
		assertEquals(tokenId, info.getTokenId());
		assertEquals(token.symbol(), info.getSymbol());
		assertEquals(token.name(), info.getName());
		assertEquals(token.treasury().toGrpcAccountId(), info.getTreasury());
		assertEquals(token.totalSupply(), info.getTotalSupply());
		assertEquals(token.decimals(), info.getDecimals());
		assertEquals(TOKEN_ADMIN_KT.asKey(), info.getAdminKey());
		assertEquals(TOKEN_FREEZE_KT.asKey(), info.getFreezeKey());
		assertEquals(TOKEN_KYC_KT.asKey(), info.getKycKey());
		assertEquals(MISC_ACCOUNT_KT.asKey(), info.getWipeKey());
		assertEquals(autoRenew, info.getAutoRenewAccount());
		assertEquals(Duration.newBuilder().setSeconds(autoRenewPeriod).build(), info.getAutoRenewPeriod());
		assertEquals(Timestamp.newBuilder().setSeconds(expiry).build(), info.getExpiry());
		assertEquals(TokenFreezeStatus.Frozen, info.getDefaultFreezeStatus());
		assertEquals(TokenKycStatus.Granted, info.getDefaultKycStatus());
	}

	@Test
	public void infoForScheduleIsUnsupported() {
		assertThrows(UnsupportedOperationException.class, () -> subject.infoForSchedule(scheduleId));
	}

	@Test
	public void scheduleExistsIsUnsupported() {
		assertThrows(UnsupportedOperationException.class, () -> subject.scheduleExists(scheduleId));
	}

	@Test
	public void getsContractInfo() throws Exception {
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
	public void returnsEmptyOptionalIfContractMissing() {
		given(contracts.get(any())).willReturn(null);

		// expect:
		assertTrue(subject.infoForContract(cid).isEmpty());
	}

	@Test
	public void handlesNullKey() {
		// given:
		contract.setKey(null);

		// when:
		var info = subject.infoForContract(cid).get();

		// then:
		assertFalse(info.hasAdminKey());
	}

	@Test
	public void getsAttrs() {
		given(attrs.get(target)).willReturn(metadata);

		// when
		var stuff = subject.attrOf(target);

		// then:
		assertEquals(metadata.toString(), stuff.get().toString());
	}

	@Test
	public void getsBytecode() {
		// when:
		var actual = subject.bytecodeOf(cid);

		// then:
		assertArrayEquals(expectedBytecode, actual.get());
	}

	@Test
	public void getsStorage() {
		// when:
		var actual = subject.storageOf(cid);

		// then:
		assertArrayEquals(expectedStorage, actual.get());
	}

	@Test
	public void getsContents() {
		given(contents.get(target)).willReturn(data);

		// when
		var stuff = subject.contentsOf(target);

		// then:
		assertTrue(Arrays.equals(data, stuff.get()));
	}

	@Test
	public void assemblesFileInfo() {
		given(attrs.get(target)).willReturn(metadata);
		given(contents.get(target)).willReturn(data);

		// when:
		var info = subject.infoForFile(target);

		// then:
		assertTrue(info.isPresent());
		assertEquals(expected, info.get());
	}

	@Test
	public void assemblesFileInfoForImmutable() {
		given(attrs.get(target)).willReturn(immutableMetadata);
		given(contents.get(target)).willReturn(data);

		// when:
		var info = subject.infoForFile(target);

		// then:
		assertTrue(info.isPresent());
		assertEquals(expectedImmutable, info.get());
	}

	@Test
	public void assemblesFileInfoForDeleted() {
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
	public void returnEmptyFileInfoForBinaryObjectNotFoundException() {
		// setup:
		given(attrs.get(target)).willThrow(new com.swirlds.blob.BinaryObjectNotFoundException());
		given(propertySource.getIntProperty("binary.object.query.retry.times")).willReturn(3);

		// when:
		var info = subject.infoForFile(target);

		// then:
		assertTrue(info.isEmpty());
	}

	@Test
	public void returnsEmptyForMissing() {
		// when:
		var info = subject.infoForFile(target);

		// then:
		assertTrue(info.isEmpty());
	}

	@Test
	public void returnsEmptyForMissingContent() {
		// when:
		var info = subject.contentsOf(target);

		// then:
		assertTrue(info.isEmpty());
	}

	@Test
	public void returnsEmptyForMissingAttr() {
		// when:
		var info = subject.attrOf(target);

		// then:
		assertTrue(info.isEmpty());
	}

	@Test
	public void returnsEmptyForTrouble() {
		given(attrs.get(any())).willThrow(IllegalArgumentException.class);

		// when:
		var info = subject.infoForFile(target);

		// then:
		assertTrue(info.isEmpty());
	}

	@Test
	public void getsSpecialFileContents() {
		FileID file150 = asFile("0.0.150");

		given(diskFs.contentsOf(file150)).willReturn(data);
		given(diskFs.contains(file150)).willReturn(true);

		// when
		var stuff = subject.contentsOf(file150);

		// then:
		assertTrue(Arrays.equals(data, stuff.get()));
	}
}
