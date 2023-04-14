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

package com.hedera.node.app.service.contract.impl.test.handlers;

import static com.hedera.node.app.service.mono.pbj.PbjConverter.toPbj;
import static com.hedera.test.factories.scenarios.ContractUpdateScenarios.CONTRACT_UPDATE_EXPIRATION_ONLY_SCENARIO;
import static com.hedera.test.factories.scenarios.ContractUpdateScenarios.CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_ADMIN_KEY_SCENARIO;
import static com.hedera.test.factories.scenarios.ContractUpdateScenarios.CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_AUTORENEW_SCENARIO;
import static com.hedera.test.factories.scenarios.ContractUpdateScenarios.CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_DEPRECATED_CID_ADMIN_KEY_SCENARIO;
import static com.hedera.test.factories.scenarios.ContractUpdateScenarios.CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_FILE_SCENARIO;
import static com.hedera.test.factories.scenarios.ContractUpdateScenarios.CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_MEMO;
import static com.hedera.test.factories.scenarios.ContractUpdateScenarios.CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_PROXY_SCENARIO;
import static com.hedera.test.factories.scenarios.ContractUpdateScenarios.CONTRACT_UPDATE_NEW_AUTO_RENEW_SCENARIO;
import static com.hedera.test.factories.scenarios.ContractUpdateScenarios.CONTRACT_UPDATE_WITH_NEW_ADMIN_KEY;
import static com.hedera.test.factories.scenarios.ContractUpdateScenarios.MISC_ACCOUNT_KT;
import static com.hedera.test.factories.scenarios.ContractUpdateScenarios.MISC_ADMIN_KT;
import static com.hedera.test.factories.scenarios.ContractUpdateScenarios.SIMPLE_NEW_ADMIN_KT;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.handlers.ContractUpdateHandler;
import com.hedera.node.app.spi.accounts.AccountAccess;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContractUpdateHandlerParityTest {
    private AccountAccess keyLookup;
    private final ContractUpdateHandler subject = new ContractUpdateHandler();

    @BeforeEach
    void setUp() {
        keyLookup = AdapterUtils.wellKnownKeyLookupAt();
    }

    @Test
    void getsContractUpdateWithAdminKey() throws PreCheckException {
        final var theTxn = txnFrom(CONTRACT_UPDATE_WITH_NEW_ADMIN_KEY);
        final var context = new PreHandleContext(keyLookup, theTxn);
        subject.preHandle(context);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(context.requiredNonPayerKeys())
                .containsExactlyInAnyOrder(
                        MISC_ADMIN_KT.asPbjKey(), SIMPLE_NEW_ADMIN_KT.asPbjKey());
    }

    @Test
    void getsContractUpdateNewExpirationTimeOnly() throws PreCheckException {
        final var theTxn = txnFrom(CONTRACT_UPDATE_EXPIRATION_ONLY_SCENARIO);
        final var context = new PreHandleContext(keyLookup, theTxn);
        subject.preHandle(context);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertTrue(context.requiredNonPayerKeys().isEmpty());
    }

    @Test
    void getsContractUpdateWithDeprecatedAdminKey() throws PreCheckException {
        final var theTxn =
                txnFrom(CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_DEPRECATED_CID_ADMIN_KEY_SCENARIO);
        final var context = new PreHandleContext(keyLookup, theTxn);
        subject.preHandle(context);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertTrue(context.requiredNonPayerKeys().isEmpty());
    }

    @Test
    void getsContractUpdateNewExpirationTimeAndAdminKey() throws PreCheckException {
        final var theTxn = txnFrom(CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_ADMIN_KEY_SCENARIO);
        final var context = new PreHandleContext(keyLookup, theTxn);
        subject.preHandle(context);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertThat(context.requiredNonPayerKeys())
                .containsExactlyInAnyOrder(
                        MISC_ADMIN_KT.asPbjKey(), SIMPLE_NEW_ADMIN_KT.asPbjKey());
    }

    @Test
    void getsContractUpdateNewExpirationTimeAndProxy() throws PreCheckException {
        final var theTxn = txnFrom(CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_PROXY_SCENARIO);
        final var context = new PreHandleContext(keyLookup, theTxn);
        subject.preHandle(context);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(context.requiredNonPayerKeys(), List.of(MISC_ADMIN_KT.asPbjKey()));
    }

    @Test
    void getsContractUpdateNewExpirationTimeAndAutoRenew() throws PreCheckException {
        final var theTxn = txnFrom(CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_AUTORENEW_SCENARIO);
        final var context = new PreHandleContext(keyLookup, theTxn);
        subject.preHandle(context);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(context.requiredNonPayerKeys(), List.of(MISC_ADMIN_KT.asPbjKey()));
    }

    @Test
    void getsContractUpdateNewExpirationTimeAndFile() throws PreCheckException {
        final var theTxn = txnFrom(CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_FILE_SCENARIO);
        final var context = new PreHandleContext(keyLookup, theTxn);
        subject.preHandle(context);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(context.requiredNonPayerKeys(), List.of(MISC_ADMIN_KT.asPbjKey()));
    }

    @Test
    void getsContractUpdateNewExpirationTimeAndMemo() throws PreCheckException {
        final var theTxn = txnFrom(CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_MEMO);
        final var context = new PreHandleContext(keyLookup, theTxn);
        subject.preHandle(context);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(context.requiredNonPayerKeys(), List.of(MISC_ADMIN_KT.asPbjKey()));
    }

    @Test
    void getsContractUpdateNewAutoRenewAccount() throws PreCheckException {
        final var theTxn = txnFrom(CONTRACT_UPDATE_NEW_AUTO_RENEW_SCENARIO);
        final var context = new PreHandleContext(keyLookup, theTxn);
        subject.preHandle(context);

        assertEquals(context.payerKey(), DEFAULT_PAYER_KT.asPbjKey());
        assertEquals(context.requiredNonPayerKeys(), List.of(MISC_ACCOUNT_KT.asPbjKey()));
    }

    private TransactionBody txnFrom(final TxnHandlingScenario scenario) {
        try {
            return toPbj(scenario.platformTxn().getTxn());
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
