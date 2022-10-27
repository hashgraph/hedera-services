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
package com.hedera.node.app.service.token.impl;

import static com.hedera.node.app.spi.key.HederaKey.asHederaKey;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asAliasAccount;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.google.protobuf.ByteString;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.state.States;
import com.hedera.node.app.state.impl.InMemoryStateImpl;
import com.hedera.node.app.state.impl.RebuiltStateImpl;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.KeyUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import java.util.Optional;
import org.hyperledger.besu.datatypes.Address;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountStoreTest {
    @Mock private RebuiltStateImpl aliases;
    @Mock private InMemoryStateImpl accounts;

    @Mock private MerkleAccount account;
    @Mock private States states;
    private Key payerKey = KeyUtils.A_COMPLEX_KEY;

    private Optional<HederaKey> payerHederaKey = asHederaKey(payerKey);
    private AccountID payerAlias = asAliasAccount(ByteString.copyFromUtf8("testAlias"));
    private AccountID payer = asAccount("0.0.3");
    private Long payerNum = 3L;
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
        given(aliases.get(payerAlias.getAlias())).willReturn(Optional.of(payerNum));
        given(accounts.get(payerNum)).willReturn(Optional.of(account));
        given(account.getAccountKey()).willReturn((JKey) payerHederaKey.get());

        final var result = subject.getKey(payerAlias);

        assertFalse(result.failed());
        assertNull(result.failureReason());
        assertEquals(payerHederaKey.get(), result.key());
    }

    @Test
    void getsKeyIfAccount() {
        given(accounts.get(payerNum)).willReturn(Optional.of(account));
        given(account.getAccountKey()).willReturn((JKey) payerHederaKey.get());

        final var result = subject.getKey(payer);

        assertFalse(result.failed());
        assertNull(result.failureReason());
        assertEquals(payerHederaKey.get(), result.key());
    }

    @Test
    void getsNullKeyIfMissingAlias() {
        given(aliases.get(payerAlias.getAlias())).willReturn(Optional.empty());

        final var result = subject.getKey(payerAlias);

        assertTrue(result.failed());
        assertEquals(INVALID_ACCOUNT_ID, result.failureReason());
        assertEquals(null, result.key());
    }

    @Test
    void getsNullKeyIfMissingAccount() {
        given(accounts.get(payerNum)).willReturn(Optional.empty());

        final var result = subject.getKey(payer);

        assertTrue(result.failed());
        assertEquals(INVALID_ACCOUNT_ID, result.failureReason());
        assertEquals(null, result.key());
    }

    @Test
    void getsMirrorAddress() {
        final var num = EntityNum.fromLong(payerNum);
        final Address mirrorAddress = num.toEvmAddress();
        final var mirrorAccount =
                asAliasAccount(ByteString.copyFrom(mirrorAddress.toArrayUnsafe()));

        given(accounts.get(payerNum)).willReturn(Optional.of(account));
        given(account.getAccountKey()).willReturn((JKey) payerHederaKey.get());

        final var result = subject.getKey(mirrorAccount);

        assertFalse(result.failed());
        assertNull(result.failureReason());
        assertEquals(payerHederaKey.get(), result.key());
    }

    @Test
    void failsIfMirrorAddressDesntExist() {
        final var num = EntityNum.fromLong(payerNum);
        final Address mirrorAddress = num.toEvmAddress();
        final var mirrorAccount =
                asAliasAccount(ByteString.copyFrom(mirrorAddress.toArrayUnsafe()));

        given(accounts.get(payerNum)).willReturn(Optional.empty());

        final var result = subject.getKey(mirrorAccount);

        assertTrue(result.failed());
        assertEquals(INVALID_ACCOUNT_ID, result.failureReason());
        assertEquals(null, result.key());
    }
}
