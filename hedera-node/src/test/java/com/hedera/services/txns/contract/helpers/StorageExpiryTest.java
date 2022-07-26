package com.hedera.services.txns.contract.helpers;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.swirlds.merkle.map.MerkleMap;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayDeque;
import java.util.Deque;

import static com.swirlds.common.utility.CommonUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class StorageExpiryTest {
	@Mock
	private AliasManager aliasManager;
	@Mock
	private MerkleMap<EntityNum, MerkleAccount> contracts;
	@Mock
	private MessageFrame stackBottomFrame;
	@Mock
	private MessageFrame stackTopFrame;
	@Mock
	private MessageFrame currentFrame;

	private Deque<MessageFrame> stack = new ArrayDeque<>();

	private StorageExpiry subject;

	@BeforeEach
	void setUp() {
		subject = new StorageExpiry(aliasManager, () -> contracts);
	}

	@Test
	void lookupUsesDefaultWhenNoRecipientIsPresentInCreation() {
		setupStackWith(aMirrorAddress, aMirrorAddress);
		given(currentFrame.getRecipientAddress()).willReturn(aMirrorAddress);
		given(aliasManager.isMirror(any(byte[].class))).willReturn(true);

		final var oracle = subject.hapiCreationOracle(hapiExpiry);

		final var answer = oracle.storageExpiryIn(currentFrame);

		assertEquals(hapiExpiry, answer);
	}

	@Test
	void lookupUsesImmediateRecipientIfPresentInMirrorForm() {
		addExtantContract(aContractNum, nonHapiExpiry);
		given(currentFrame.getRecipientAddress()).willReturn(aMirrorAddress);
		given(aliasManager.isMirror(any(byte[].class))).willReturn(true);

		final var oracle = subject.hapiCreationOracle(hapiExpiry);

		final var answer = oracle.storageExpiryIn(currentFrame);

		assertEquals(nonHapiExpiry, answer);
	}

	@Test
	void lookupUsesImmediateRecipientIfPresentInAliasForm() {
		addExtantContract(aContractNum, nonHapiExpiry);
		given(currentFrame.getRecipientAddress()).willReturn(nonMirrorAddress);
		given(aliasManager.lookupIdBy(ByteString.copyFrom(nonMirrorAddress.toArrayUnsafe()))).willReturn(aContractNum);

		final var oracle = subject.hapiCreationOracle(hapiExpiry);

		final var answer = oracle.storageExpiryIn(currentFrame);

		assertEquals(nonHapiExpiry, answer);
	}

	@Test
	void usesFirstEncounteredKnownExpiryFromStack() {
		setupStackWith(bMirrorAddress, aMirrorAddress);
		addExtantContract(bContractNum, nonHapiExpiry);
		given(contracts.containsKey(aContractNum)).willReturn(false);
		given(aliasManager.isMirror(aMirrorAddress.toArrayUnsafe())).willReturn(true);
		given(aliasManager.isMirror(bMirrorAddress.toArrayUnsafe())).willReturn(true);
		given(aliasManager.isMirror(nonMirrorAddress.toArrayUnsafe())).willReturn(false);
		given(currentFrame.getRecipientAddress()).willReturn(nonMirrorAddress);
		given(aliasManager.lookupIdBy(ByteString.copyFrom(nonMirrorAddress.toArrayUnsafe()))).willReturn(aContractNum);

		final var oracle = subject.hapiCallOracle();

		final var answer = oracle.storageExpiryIn(currentFrame);

		assertEquals(nonHapiExpiry, answer);
	}

	@Test
	void doesntThrowWhenNothingIsFoundAtAll() {
		setupStackWith(bMirrorAddress, aMirrorAddress);
		given(contracts.containsKey(aContractNum)).willReturn(false);
		given(aliasManager.isMirror(aMirrorAddress.toArrayUnsafe())).willReturn(true);
		given(aliasManager.isMirror(bMirrorAddress.toArrayUnsafe())).willReturn(true);
		given(aliasManager.isMirror(nonMirrorAddress.toArrayUnsafe())).willReturn(false);
		given(currentFrame.getRecipientAddress()).willReturn(nonMirrorAddress);
		given(aliasManager.lookupIdBy(ByteString.copyFrom(nonMirrorAddress.toArrayUnsafe()))).willReturn(aContractNum);

		final var oracle = subject.hapiCallOracle();

		final var answer = oracle.storageExpiryIn(currentFrame);

		assertEquals(0L, answer);
	}

	@Test
	void staticCallOracleIsUnusable() {
		final var oracle = subject.hapiStaticCallOracle();

		assertEquals(0, oracle.storageExpiryIn(currentFrame));
	}

	private void setupStackWith(final Address bottomRecipient, final Address topRecipient) {
		given(stackBottomFrame.getRecipientAddress()).willReturn(bottomRecipient);
		given(stackTopFrame.getRecipientAddress()).willReturn(topRecipient);

		stack.push(stackBottomFrame);
		stack.push(stackTopFrame);

		given(currentFrame.getMessageFrameStack()).willReturn(stack);
	}

	private void addExtantContract(final EntityNum num, final long expiry) {
		given(contracts.containsKey(num)).willReturn(true);
		final var contract = mock(MerkleAccount.class);
		given(contracts.get(num)).willReturn(contract);
		given(contract.getExpiry()).willReturn(expiry);
	}

	private static final long hapiExpiry = 1_234_567L;
	private static final long nonHapiExpiry = 6_666_666L;
	private static final EntityNum aContractNum = EntityNum.fromLong(1234L);
	private static final EntityNum bContractNum = EntityNum.fromLong(1235L);
	private static final byte[] rawNonMirrorAddress = unhex("abcdefabcdefabcdefbabcdefabcdefabcdefbbb");
	private static final Address nonMirrorAddress = Address.wrap(Bytes.wrap(rawNonMirrorAddress));
	private static final Address aMirrorAddress = aContractNum.toEvmAddress();
	private static final Address bMirrorAddress = bContractNum.toEvmAddress();
}
