/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.node.app.service.token.impl.test;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKeyList;
import com.hedera.node.app.service.mono.state.impl.InMemoryStateImpl;
import com.hedera.node.app.service.mono.state.impl.RebuiltStateImpl;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.token.impl.AccountStore;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.state.States;
import com.hedera.test.utils.KeyUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asAliasAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ALIAS_IS_IMMUTABLE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

// FUTURE: Once we have protobuf generated object need to replace all JKeys.
@ExtendWith(MockitoExtension.class)
class AccountStoreTest {
	@Mock
	private RebuiltStateImpl aliases;
	@Mock
	private InMemoryStateImpl accounts;

	@Mock
	private MerkleAccount account;
	@Mock
	private States states;
	private static final HederaKey PAYER_HEDERA_KEY = asHederaKey(KeyUtils.A_COMPLEX_KEY).get();
	private static final AccountID PAYER_ALIAS = asAliasAccount(ByteString.copyFromUtf8("testAlias"));
	private static final AccountID PAYER = asAccount("0.0.3");
	private static final Long PAYER_NUM = 3L;
	private static final String ACCOUNTS = "ACCOUNTS";
	private static final String ALIASES = "ALIASES";

	private AccountStore subject;

	@BeforeEach
	public void setUp() {
		given(states.get(ACCOUNTS)).willReturn(accounts);
		given(states.get(ALIASES)).willReturn(aliases);
		subject = new AccountStore(states);
	}

	@Test
	void getsKeyIfAlias() {
		given(aliases.get(PAYER_ALIAS.getAlias())).willReturn(Optional.of(PAYER_NUM));
		given(accounts.get(PAYER_NUM)).willReturn(Optional.of(account));
		given(account.getAccountKey()).willReturn((JKey) PAYER_HEDERA_KEY);

		final var result = subject.getKey(PAYER_ALIAS);

		assertFalse(result.failed());
		assertNull(result.failureReason());
		assertEquals(PAYER_HEDERA_KEY, result.key());
	}

	@Test
	void getsKeyIfAccount() {
		given(accounts.get(PAYER_NUM)).willReturn(Optional.of(account));
		given(account.getAccountKey()).willReturn((JKey) PAYER_HEDERA_KEY);

		final var result = subject.getKey(PAYER);

		assertFalse(result.failed());
		assertNull(result.failureReason());
		assertEquals(PAYER_HEDERA_KEY, result.key());
	}

	@Test
	void getsNullKeyIfMissingAlias() {
		given(aliases.get(PAYER_ALIAS.getAlias())).willReturn(Optional.empty());

		final var result = subject.getKey(PAYER_ALIAS);

		assertTrue(result.failed());
		assertEquals(INVALID_ACCOUNT_ID, result.failureReason());
		assertEquals(null, result.key());
	}

	@Test
	void getsNullKeyIfMissingAccount() {
		given(accounts.get(PAYER_NUM)).willReturn(Optional.empty());

		final var result = subject.getKey(PAYER);

		assertTrue(result.failed());
		assertEquals(INVALID_ACCOUNT_ID, result.failureReason());
		assertEquals(null, result.key());
	}

	@Test
	void getsMirrorAddress() {
		final var num = EntityNum.fromLong(PAYER_NUM);
		final Address mirrorAddress = num.toEvmAddress();
		final var mirrorAccount =
				asAliasAccount(ByteString.copyFrom(mirrorAddress.toArrayUnsafe()));

		given(accounts.get(PAYER_NUM)).willReturn(Optional.of(account));
		given(account.getAccountKey()).willReturn((JKey) PAYER_HEDERA_KEY);

		final var result = subject.getKey(mirrorAccount);

		assertFalse(result.failed());
		assertNull(result.failureReason());
		assertEquals(PAYER_HEDERA_KEY, result.key());
	}

	@Test
	void failsIfMirrorAddressDoesntExist() {
		final var num = EntityNum.fromLong(PAYER_NUM);
		final Address mirrorAddress = num.toEvmAddress();
		final var mirrorAccount =
				asAliasAccount(ByteString.copyFrom(mirrorAddress.toArrayUnsafe()));

		given(accounts.get(PAYER_NUM)).willReturn(Optional.empty());

		final var result = subject.getKey(mirrorAccount);

		assertTrue(result.failed());
		assertEquals(INVALID_ACCOUNT_ID, result.failureReason());
		assertEquals(null, result.key());
	}

	@Test
	void getsKeyIfPayerAliasAndReceiverSigRequired() {
		given(aliases.get(PAYER_ALIAS.getAlias())).willReturn(Optional.of(PAYER_NUM));
		given(accounts.get(PAYER_NUM)).willReturn(Optional.of(account));
		given(account.getAccountKey()).willReturn((JKey) PAYER_HEDERA_KEY);
		given(account.isReceiverSigRequired()).willReturn(true);

		final var result = subject.getKeyIfReceiverSigRequired(PAYER_ALIAS);

		assertFalse(result.failed());
		assertNull(result.failureReason());
		assertEquals(PAYER_HEDERA_KEY, result.key());
	}

	@Test
	void getsKeyIfPayerAccountAndReceiverSigRequired() {
		given(accounts.get(PAYER_NUM)).willReturn(Optional.of(account));
		given(account.getAccountKey()).willReturn((JKey) PAYER_HEDERA_KEY);
		given(account.isReceiverSigRequired()).willReturn(true);

		final var result = subject.getKeyIfReceiverSigRequired(PAYER);

		assertFalse(result.failed());
		assertNull(result.failureReason());
		assertEquals(PAYER_HEDERA_KEY, result.key());
	}

	@Test
	void getsNullKeyFromReceiverSigRequiredIfMissingAlias() {
		given(aliases.get(PAYER_ALIAS.getAlias())).willReturn(Optional.empty());

		final var result = subject.getKeyIfReceiverSigRequired(PAYER_ALIAS);

		assertTrue(result.failed());
		assertEquals(INVALID_ACCOUNT_ID, result.failureReason());
		assertEquals(null, result.key());
	}

	@Test
	void getsNullKeyFromReceiverSigRequiredIfMissingAccount() {
		given(accounts.get(PAYER_NUM)).willReturn(Optional.empty());

		final var result = subject.getKeyIfReceiverSigRequired(PAYER);

		assertTrue(result.failed());
		assertEquals(INVALID_ACCOUNT_ID, result.failureReason());
		assertEquals(null, result.key());
	}

	@Test
	void getsNullKeyIfAndReceiverSigNotRequired() {
		given(aliases.get(PAYER_ALIAS.getAlias())).willReturn(Optional.of(PAYER_NUM));
		given(accounts.get(PAYER_NUM)).willReturn(Optional.of(account));
		given(account.isReceiverSigRequired()).willReturn(false);

		final var result = subject.getKeyIfReceiverSigRequired(PAYER_ALIAS);

		assertFalse(result.failed());
		assertNull(result.failureReason());
		assertNull(result.key());
	}

	@Test
	void getsNullKeyFromAccountIfReceiverKeyNotRequired() {
		given(accounts.get(PAYER_NUM)).willReturn(Optional.of(account));
		given(account.isReceiverSigRequired()).willReturn(false);

		final var result = subject.getKeyIfReceiverSigRequired(PAYER);

		assertFalse(result.failed());
		assertNull(result.failureReason());
		assertNull(result.key());
	}

	@Test
	void failsKeyValidationWhenKeyReturnedIsNull() {
		given(accounts.get(PAYER_NUM)).willReturn(Optional.of(account));
		given(account.getAccountKey()).willReturn(null);

		assertThrows(IllegalArgumentException.class, () -> subject.getKey(PAYER));

		given(account.isReceiverSigRequired()).willReturn(true);
		assertThrows(
				IllegalArgumentException.class, () -> subject.getKeyIfReceiverSigRequired(PAYER));
	}

	@Test
	void failsKeyValidationWhenKeyReturnedIsEmpty() {
		given(accounts.get(PAYER_NUM)).willReturn(Optional.of(account));
		given(account.getAccountKey()).willReturn(new JKeyList());

		var result = subject.getKey(PAYER);

		assertTrue(result.failed());
		assertEquals(ALIAS_IS_IMMUTABLE, result.failureReason());
		assertNull(result.key());

		given(account.isReceiverSigRequired()).willReturn(true);
		result = subject.getKeyIfReceiverSigRequired(PAYER);

		assertTrue(result.failed());
		assertEquals(ALIAS_IS_IMMUTABLE, result.failureReason());
		assertNull(result.key());
	}
}
