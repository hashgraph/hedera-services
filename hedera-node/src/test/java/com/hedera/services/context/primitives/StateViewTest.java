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
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_ADMIN_KT;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_FREEZE_KT;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asContract;
import static com.hedera.test.utils.IdUtils.asFile;
import static com.hedera.test.utils.IdUtils.asToken;
import static org.junit.jupiter.api.Assertions.*;

import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.tokens.TokenStore;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.test.factories.accounts.MapValueFactory;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileGetInfoResponse;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hedera.services.legacy.core.jproto.JFileInfo;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenRef;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.platform.runner.JUnitPlatform;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Map;

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
	TokenRef foundToken = TokenRef.newBuilder().setSymbol("FOUND").build();
	TokenRef missingToken = TokenRef.newBuilder().setSymbol("MISSING").build();
	ContractID cid = asContract("3.2.1");
	byte[] cidAddress = asSolidityAddress((int) cid.getShardNum(), cid.getRealmNum(), cid.getContractNum());
	ContractID notCid = asContract("1.2.3");

	FileGetInfoResponse.FileInfo expected;
	FileGetInfoResponse.FileInfo expectedImmutable;

	Map<byte[], byte[]> storage;
	Map<byte[], byte[]> bytecode;
	Map<FileID, byte[]> contents;
	Map<FileID, JFileInfo> attrs;

	FCMap<MerkleEntityId, MerkleAccount> contracts;
	TokenStore tokenStore;

	MerkleToken token;
	MerkleAccount contract;
	MerkleAccount notContract;
	PropertySource propertySource;

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

		notContract = MapValueFactory.newAccount()
				.isSmartContract(false)
				.get();
		contract = MapValueFactory.newAccount()
				.memo("Stay cold...")
				.isSmartContract(true)
				.accountKeys(COMPLEX_KEY_ACCOUNT_KT)
				.proxy(asAccount("1.2.3"))
				.senderThreshold(1_234L)
				.receiverThreshold(4_321L)
				.receiverSigRequired(true)
				.balance(555L)
				.autoRenewPeriod(1_000_000L)
				.expirationTime(9_999_999L)
				.get();
		contracts = (FCMap<MerkleEntityId, MerkleAccount>)mock(FCMap.class);
		given(contracts.get(MerkleEntityId.fromContractId(cid))).willReturn(contract);
		given(contracts.get(MerkleEntityId.fromContractId(notCid))).willReturn(notContract);

		tokenStore = mock(TokenStore.class);
		token = new MerkleToken(
				100, 1,
				TxnHandlingScenario.TOKEN_ADMIN_KT.asJKey(),
				"UnfrozenToken", true, true,
				new EntityId(1, 2, 3));
		token.setFreezeKey(TxnHandlingScenario.TOKEN_FREEZE_KT.asJKey());
		given(tokenStore.resolve(foundToken)).willReturn(tokenId);
		given(tokenStore.resolve(missingToken)).willReturn(TokenStore.MISSING_TOKEN);
		given(tokenStore.get(tokenId)).willReturn(token);

		contents = mock(Map.class);
		attrs = mock(Map.class);
		storage = mock(Map.class);
		bytecode = mock(Map.class);
		given(storage.get(argThat((byte[] bytes) -> Arrays.equals(cidAddress, bytes)))).willReturn(expectedStorage);
		given(bytecode.get(argThat((byte[] bytes) -> Arrays.equals(cidAddress, bytes)))).willReturn(expectedBytecode);
		propertySource = mock(PropertySource.class);

		subject = new StateView(tokenStore, StateView.EMPTY_TOPICS_SUPPLIER, () -> contracts, propertySource);
		subject.fileAttrs = attrs;
		subject.fileContents = contents;
		subject.contractBytecode = bytecode;
		subject.contractStorage = storage;
	}

	@Test
	public void tokenExistsWorks() {
		// expect:
		assertTrue(subject.tokenExists(foundToken));
		assertFalse(subject.tokenExists(missingToken));
	}

	@Test
	public void recognizesMissingToken() {
		// when:
		var info = subject.infoForToken(missingToken);

		// then:
		assertTrue(info.isEmpty());
	}

	@Test
	public void failsGracefully() {
		given(tokenStore.get(any())).willThrow(IllegalArgumentException.class);

		// when:
		var info = subject.infoForToken(foundToken);

		// then:
		assertTrue(info.isEmpty());
	}

	@Test
	public void getsTokenInfoMinusFreezeIfMissing() {
		// setup:
		token.setFreezeKey(MerkleToken.UNUSED_KEY);

		// when:
		var info = subject.infoForToken(foundToken).get();

		// then:
		assertEquals(tokenId, info.getTokenId());
		assertEquals(token.symbol(), info.getSymbol());
		assertEquals(token.treasury().toGrpcAccountId(), info.getTreasury());
		assertEquals(token.tokenFloat(), info.getCurrentFloat());
		assertEquals(token.divisibility(), info.getDivisibility());
		assertEquals(TOKEN_ADMIN_KT.asKey(), info.getAdminKey());
		assertEquals(false, info.getFreezeDefault());
		assertFalse(info.hasFreezeKey());
	}

	@Test
	public void getsTokenInfo() {
		// when:
		var info = subject.infoForToken(foundToken).get();

		// then:
		assertEquals(tokenId, info.getTokenId());
		assertEquals(token.symbol(), info.getSymbol());
		assertEquals(token.treasury().toGrpcAccountId(), info.getTreasury());
		assertEquals(token.tokenFloat(), info.getCurrentFloat());
		assertEquals(token.divisibility(), info.getDivisibility());
		assertEquals(TOKEN_ADMIN_KT.asKey(), info.getAdminKey());
		assertEquals(TOKEN_FREEZE_KT.asKey(), info.getFreezeKey());
		assertEquals(token.accountsAreFrozenByDefault(), info.getFreezeDefault());
	}

	@Test
	public void getsContractInfo() throws Exception {
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
}
