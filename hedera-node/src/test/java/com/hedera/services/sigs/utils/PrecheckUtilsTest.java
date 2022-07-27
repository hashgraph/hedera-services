/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.sigs.utils;

import static com.hedera.test.factories.txns.CryptoTransferFactory.newSignedCryptoTransfer;
import static com.hedera.test.factories.txns.CryptoUpdateFactory.newSignedCryptoUpdate;
import static com.hedera.test.factories.txns.PlatformTxnFactory.from;
import static com.hedera.test.factories.txns.TinyBarsFromTo.tinyBarsFromTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.BDDMockito.given;

import com.hedera.services.context.NodeInfo;
import com.hedera.services.utils.accessors.PlatformTxnAccessor;
import com.hedera.test.factories.txns.SignedTxnFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.function.Predicate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PrecheckUtilsTest {
    private static final String nodeId = SignedTxnFactory.DEFAULT_NODE_ID;
    private static final AccountID node = SignedTxnFactory.DEFAULT_NODE;

    @Mock private NodeInfo nodeInfo;

    private Predicate<TransactionBody> subject;

    @BeforeEach
    void setUp() {
        subject = PrecheckUtils.queryPaymentTestFor(nodeInfo);
    }

    @Test
    void queryPaymentsMustBeCryptoTransfers() throws Throwable {
        final var txn =
                PlatformTxnAccessor.from(from(newSignedCryptoUpdate("0.0.2").get())).getTxn();

        assertFalse(subject.test(txn));
    }

    @Test
    void transferWithoutTargetNodeIsNotQueryPayment() throws Throwable {
        given(nodeInfo.selfAccount()).willReturn(node);
        final var txn =
                PlatformTxnAccessor.from(
                                from(
                                        newSignedCryptoTransfer()
                                                .transfers(
                                                        tinyBarsFromTo(
                                                                "0.0.1024", "0.0.2048", 1_000L))
                                                .get()))
                        .getTxn();

        assertFalse(subject.test(txn));
    }

    @Test
    void queryPaymentTransfersToTargetNode() throws Throwable {
        given(nodeInfo.selfAccount()).willReturn(node);
        final var txn =
                PlatformTxnAccessor.from(
                                from(
                                        newSignedCryptoTransfer()
                                                .transfers(
                                                        tinyBarsFromTo(nodeId, "0.0.2048", 1_000L))
                                                .get()))
                        .getTxn();

        assertFalse(subject.test(txn));
    }
}
