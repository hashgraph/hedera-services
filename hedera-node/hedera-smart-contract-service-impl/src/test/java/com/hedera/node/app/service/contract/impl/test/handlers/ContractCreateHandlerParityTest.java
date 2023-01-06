/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.contract.impl.test.handlers;

import com.hedera.node.app.service.contract.impl.handlers.ContractCreateHandler;
import com.hedera.node.app.service.token.impl.test.util.SigReqAdapterUtils;
import com.hedera.node.app.spi.AccountKeyLookup;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.AdapterUtils;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.hamcrest.collection.IsIterableContainingInOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.hedera.test.factories.scenarios.ContractCreateScenarios.CONTRACT_CREATE_WITH_AUTO_RENEW_ACCOUNT;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.FIRST_TOKEN_SENDER_KT;
import static com.hedera.test.factories.scenarios.CryptoTransferScenarios.MISC_ACCOUNT_KT;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;
import static com.hedera.test.utils.KeyUtils.sanityRestored;
import static org.assertj.core.api.FactoryBasedNavigableListAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.iterableWithSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContractCreateHandlerParityTest {
    private AccountKeyLookup keyLookup;
    private final ContractCreateHandler subject = new ContractCreateHandler();

    @BeforeEach
    void setUp() {
        final var now = Instant.now();
        keyLookup = AdapterUtils.wellKnownKeyLookupAt(now);
        readableTokenStore = SigReqAdapterUtils.wellKnownTokenStoreAt(now);
    }

    @Test
    void getsContractCreateWithAutoRenew() {
        final var theTxn = txnFrom(CONTRACT_CREATE_WITH_AUTO_RENEW_ACCOUNT);
        final var summary = subject.preHandle(theTxn,
                theTxn.getTransactionID().getAccountID(),
                keyLookup);

        assertEquals(sanityRestored(meta.payerKey()), DEFAULT_PAYER_KT.asKey());
        assertThat(
                sanityRestored(meta.requiredNonPayerKeys()),
                contains(FIRST_TOKEN_SENDER_KT.asKey()));

        assertThat(summary.getOrderedKeys(), iterableWithSize(1));
        assertThat(sanityRestored(summary.getOrderedKeys()), IsIterableContainingInOrder.contains(MISC_ACCOUNT_KT.asKey()));
    }

    private void assertMetaFailedWithReqPayerKeyAnd(
            final TransactionMetadata meta, final ResponseCodeEnum expectedFailure) {
        assertTrue(meta.failed());
        assertEquals(expectedFailure, meta.status());
        assertEquals(sanityRestored(meta.payerKey()), DEFAULT_PAYER_KT.asKey());
        assertTrue(meta.requiredNonPayerKeys().isEmpty());
    }

    private void assertMetaFailedWithReqPayerKeyAnd(
            final TransactionMetadata meta,
            final ResponseCodeEnum expectedFailure,
            final Key aNonPayerKey) {
        assertTrue(meta.failed());
        assertEquals(expectedFailure, meta.status());
        assertEquals(sanityRestored(meta.payerKey()), DEFAULT_PAYER_KT.asKey());
        assertThat(sanityRestored(meta.requiredNonPayerKeys()), contains(aNonPayerKey));
    }

    private TransactionBody txnFrom(final TxnHandlingScenario scenario) {
        try {
            return scenario.platformTxn().getTxn();
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
