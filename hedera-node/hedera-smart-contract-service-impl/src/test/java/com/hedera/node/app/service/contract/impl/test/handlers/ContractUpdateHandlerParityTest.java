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

import static com.hedera.test.factories.scenarios.ContractUpdateScenarios.*;

import com.hedera.node.app.service.contract.impl.handlers.ContractUpdateHandler;
import com.hedera.node.app.spi.AccountKeyLookup;

class ContractUpdateHandlerParityTest {
    private AccountKeyLookup keyLookup;
    private final ContractUpdateHandler subject = new ContractUpdateHandler();
    //
    //    @BeforeEach
    //    void setUp() {
    //        final var now = Instant.now();
    //        keyLookup = AdapterUtils.wellKnownKeyLookupAt();
    //    }
    //
    //    @Test
    //    void getsContractUpdateWithAdminKey() {
    //        final var theTxn = txnFrom(CONTRACT_UPDATE_WITH_NEW_ADMIN_KEY);
    //        final var meta =
    //                subject.preHandle(theTxn, theTxn.getTransactionID().getAccountID(),
    // keyLookup);
    //
    //        assertEquals(sanityRestored(meta.payerKey()), DEFAULT_PAYER_KT.asKey());
    //        assertThat(
    //                sanityRestored(meta.requiredNonPayerKeys()),
    //                contains(MISC_ADMIN_KT.asKey(), SIMPLE_NEW_ADMIN_KT.asKey()));
    //    }
    //
    //    @Test
    //    void getsContractUpdateNewExpirationTimeOnly() {
    //        final var theTxn = txnFrom(CONTRACT_UPDATE_EXPIRATION_ONLY_SCENARIO);
    //        final var meta =
    //                subject.preHandle(theTxn, theTxn.getTransactionID().getAccountID(),
    // keyLookup);
    //
    //        assertEquals(sanityRestored(meta.payerKey()), DEFAULT_PAYER_KT.asKey());
    //        assertTrue(sanityRestored(meta.requiredNonPayerKeys()).isEmpty());
    //    }
    //
    //    @Test
    //    void getsContractUpdateWithDeprecatedAdminKey() {
    //        final var theTxn =
    //
    // txnFrom(CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_DEPRECATED_CID_ADMIN_KEY_SCENARIO);
    //        final var meta =
    //                subject.preHandle(theTxn, theTxn.getTransactionID().getAccountID(),
    // keyLookup);
    //
    //        assertEquals(sanityRestored(meta.payerKey()), DEFAULT_PAYER_KT.asKey());
    //        assertTrue(sanityRestored(meta.requiredNonPayerKeys()).isEmpty());
    //    }
    //
    //    @Test
    //    void getsContractUpdateNewExpirationTimeAndAdminKey() {
    //        final var theTxn = txnFrom(CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_ADMIN_KEY_SCENARIO);
    //        final var meta =
    //                subject.preHandle(theTxn, theTxn.getTransactionID().getAccountID(),
    // keyLookup);
    //
    //        assertEquals(sanityRestored(meta.payerKey()), DEFAULT_PAYER_KT.asKey());
    //        assertThat(
    //                sanityRestored(meta.requiredNonPayerKeys()),
    //                contains(MISC_ADMIN_KT.asKey(), SIMPLE_NEW_ADMIN_KT.asKey()));
    //    }
    //
    //    @Test
    //    void getsContractUpdateNewExpirationTimeAndProxy() {
    //        final var theTxn = txnFrom(CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_PROXY_SCENARIO);
    //        final var meta =
    //                subject.preHandle(theTxn, theTxn.getTransactionID().getAccountID(),
    // keyLookup);
    //
    //        assertEquals(sanityRestored(meta.payerKey()), DEFAULT_PAYER_KT.asKey());
    //        assertThat(sanityRestored(meta.requiredNonPayerKeys()),
    // contains(MISC_ADMIN_KT.asKey()));
    //    }
    //
    //    @Test
    //    void getsContractUpdateNewExpirationTimeAndAutoRenew() {
    //        final var theTxn = txnFrom(CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_AUTORENEW_SCENARIO);
    //        final var meta =
    //                subject.preHandle(theTxn, theTxn.getTransactionID().getAccountID(),
    // keyLookup);
    //
    //        assertEquals(sanityRestored(meta.payerKey()), DEFAULT_PAYER_KT.asKey());
    //        assertThat(sanityRestored(meta.requiredNonPayerKeys()),
    // contains(MISC_ADMIN_KT.asKey()));
    //    }
    //
    //    @Test
    //    void getsContractUpdateNewExpirationTimeAndFile() {
    //        final var theTxn = txnFrom(CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_FILE_SCENARIO);
    //        final var meta =
    //                subject.preHandle(theTxn, theTxn.getTransactionID().getAccountID(),
    // keyLookup);
    //
    //        assertEquals(sanityRestored(meta.payerKey()), DEFAULT_PAYER_KT.asKey());
    //        assertThat(sanityRestored(meta.requiredNonPayerKeys()),
    // contains(MISC_ADMIN_KT.asKey()));
    //    }
    //
    //    @Test
    //    void getsContractUpdateNewExpirationTimeAndMemo() {
    //        final var theTxn = txnFrom(CONTRACT_UPDATE_EXPIRATION_PLUS_NEW_MEMO);
    //        final var meta =
    //                subject.preHandle(theTxn, theTxn.getTransactionID().getAccountID(),
    // keyLookup);
    //
    //        assertEquals(sanityRestored(meta.payerKey()), DEFAULT_PAYER_KT.asKey());
    //        assertThat(sanityRestored(meta.requiredNonPayerKeys()),
    // contains(MISC_ADMIN_KT.asKey()));
    //    }
    //
    //    @Test
    //    void getsContractUpdateNewAutoRenewAccount() {
    //        final var theTxn = txnFrom(CONTRACT_UPDATE_NEW_AUTO_RENEW_SCENARIO);
    //        final var meta =
    //                subject.preHandle(theTxn, theTxn.getTransactionID().getAccountID(),
    // keyLookup);
    //
    //        assertEquals(sanityRestored(meta.payerKey()), DEFAULT_PAYER_KT.asKey());
    //        assertThat(sanityRestored(meta.requiredNonPayerKeys()),
    // contains(MISC_ACCOUNT_KT.asKey()));
    //    }
    //
    //    private TransactionBody txnFrom(final TxnHandlingScenario scenario) {
    //        try {
    //            return scenario.platformTxn().getTxn();
    //        } catch (final Throwable e) {
    //            throw new RuntimeException(e);
    //        }
    //    }
}
