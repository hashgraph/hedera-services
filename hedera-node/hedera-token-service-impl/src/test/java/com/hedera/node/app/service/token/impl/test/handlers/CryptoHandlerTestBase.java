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

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.KeyUtils.A_COMPLEX_KEY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.impl.InMemoryStateImpl;
import com.hedera.node.app.service.mono.state.impl.RebuiltStateImpl;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.token.impl.CryptoSignatureWaiversImpl;
import com.hedera.node.app.service.token.impl.ReadableAccountStore;
import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.hedera.node.app.spi.numbers.HederaFileNumbers;
import com.hedera.node.app.spi.state.States;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Timestamp;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class CryptoHandlerTestBase {
    protected static final String ACCOUNTS = "ACCOUNTS";
    protected static final String ALIASES = "ALIASES";
    protected final Key key = A_COMPLEX_KEY;
    protected final AccountID payer = asAccount("0.0.3");
    protected final Timestamp consensusTimestamp =
            Timestamp.newBuilder().setSeconds(1_234_567L).build();
    protected final HederaKey payerKey = asHederaKey(A_COMPLEX_KEY).get();
    protected final Long payerNum = payer.getAccountNum();

    @Mock protected RebuiltStateImpl aliases;
    @Mock protected InMemoryStateImpl accounts;
    @Mock protected MerkleAccount payerAccount;
    @Mock protected States states;
    @Mock protected HederaAccountNumbers accountNumbers;
    @Mock protected HederaFileNumbers fileNumbers;
    @Mock protected CryptoSignatureWaiversImpl waivers;
    @Mock protected TransactionMetadata metaToHandle;
    protected ReadableAccountStore store;
    protected PreHandleContext context;

    @BeforeEach
    void commonSetUp() {
        given(states.get(ACCOUNTS)).willReturn(accounts);
        given(states.get(ALIASES)).willReturn(aliases);
        store = new ReadableAccountStore(states);
        context = new PreHandleContext(accountNumbers, fileNumbers, store);
        setUpPayer();
    }

    protected void basicMetaAssertions(
            final TransactionMetadata meta,
            final int keysSize,
            final boolean failed,
            final ResponseCodeEnum failureStatus) {
        assertEquals(keysSize, meta.requiredNonPayerKeys().size());
        assertTrue(failed ? meta.failed() : !meta.failed());
        assertEquals(failureStatus, meta.status());
    }

    protected void setUpPayer() {
        lenient().when(accounts.get(payerNum)).thenReturn(Optional.of(payerAccount));
        lenient().when(payerAccount.getAccountKey()).thenReturn((JKey) payerKey);
    }
}
