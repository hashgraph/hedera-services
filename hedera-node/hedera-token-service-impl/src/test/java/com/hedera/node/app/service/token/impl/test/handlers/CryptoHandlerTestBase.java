/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.test.handlers;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.virtual.EntityNumValue;
import com.hedera.node.app.service.mono.state.virtual.EntityNumVirtualKey;
import com.hedera.node.app.service.token.impl.CryptoSignatureWaiversImpl;
import com.hedera.node.app.service.token.impl.ReadableAccountStore;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
public class CryptoHandlerTestBase {
    protected static final String ACCOUNTS = "ACCOUNTS";
    protected static final String ALIASES = "ALIASES";
    protected final Key key = A_COMPLEX_KEY;
    protected final AccountID payer = AccountID.newBuilder().accountNum(3).build();
    protected final Timestamp consensusTimestamp =
            Timestamp.newBuilder().seconds(1_234_567L).build();
    protected final HederaKey payerKey = asHederaKey(A_COMPLEX_KEY).get();
    protected final Long payerNum = payer.accountNum();

    @Mock
    protected ReadableKVState<String, EntityNumValue> aliases;

    @Mock
    protected ReadableKVState<EntityNumVirtualKey, MerkleAccount> accounts;

    @Mock
    protected MerkleAccount payerAccount;

    @Mock
    protected ReadableStates states;

    @Mock
    protected CryptoSignatureWaiversImpl waivers;

    protected ReadableAccountStore store;

    @BeforeEach
    void commonSetUp() {
        given(states.<EntityNumVirtualKey, MerkleAccount>get(ACCOUNTS)).willReturn(accounts);
        given(states.<String, EntityNumValue>get(ALIASES)).willReturn(aliases);
        store = new ReadableAccountStore(states);
        setUpPayer();
    }

    protected void basicMetaAssertions(
            final PreHandleContext context,
            final int keysSize,
            final boolean failed,
            final ResponseCodeEnum failureStatus) {
        assertThat(context.getRequiredNonPayerKeys()).hasSize(keysSize);
        assertThat(context.failed()).isEqualTo(failed);
        assertThat(context.getStatus()).isEqualTo(failureStatus);
    }

    protected void setUpPayer() {
        lenient().when(accounts.get(EntityNumVirtualKey.fromLong(payerNum))).thenReturn(payerAccount);
        lenient().when(payerAccount.getAccountKey()).thenReturn((JKey) payerKey);
    }
}
