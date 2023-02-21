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

import static com.hedera.test.factories.scenarios.ContractDeleteScenarios.*;

class ContractDeleteHandlerParityTest {
    //    private AccountKeyLookup keyLookup;
    //    private final ContractDeleteHandler subject = new ContractDeleteHandler();
    //
    //    @BeforeEach
    //    void setUp() {
    //        keyLookup = AdapterUtils.wellKnownKeyLookupAt();
    //    }
    //
    //    @Test
    //    void getsContractDeleteImmutable() {
    //        final var theTxn = txnFrom(CONTRACT_DELETE_IMMUTABLE_SCENARIO);
    //        final var meta =
    //                subject.preHandle(theTxn, theTxn.transactionID().accountID(), keyLookup);
    //
    //        assertEquals(sanityRestored(meta.payerKey()), DEFAULT_PAYER_KT.asKey());
    //        assertTrue(sanityRestored(meta.requiredNonPayerKeys()).isEmpty());
    //        assertEquals(MODIFYING_IMMUTABLE_CONTRACT, meta.status());
    //    }
    //
    //    @Test
    //    void getsContractDelete() {
    //        final var theTxn = txnFrom(CONTRACT_DELETE_XFER_ACCOUNT_SCENARIO);
    //        final var meta =
    //                subject.preHandle(theTxn, theTxn.transactionID().accountID(), keyLookup);
    //
    //        assertEquals(sanityRestored(meta.payerKey()), DEFAULT_PAYER_KT.asKey());
    //        assertThat(
    //                sanityRestored(meta.requiredNonPayerKeys()),
    //                contains(MISC_ADMIN_KT.asKey(), RECEIVER_SIG_KT.asKey()));
    //    }
    //
    //    @Test
    //    void getsContractDeleteMissingAccountBeneficiary() {
    //        final var theTxn = txnFrom(CONTRACT_DELETE_MISSING_ACCOUNT_BENEFICIARY_SCENARIO);
    //        final var meta =
    //                subject.preHandle(theTxn, theTxn.transactionID().accountID(), keyLookup);
    //
    //        assertEquals(sanityRestored(meta.payerKey()), DEFAULT_PAYER_KT.asKey());
    //        assertThat(sanityRestored(meta.requiredNonPayerKeys()),
    // contains(MISC_ADMIN_KT.asKey()));
    //        assertEquals(INVALID_TRANSFER_ACCOUNT_ID, meta.status());
    //    }
    //
    //    @Test
    //    void getsContractDeleteMissingContractBeneficiary() {
    //        final var theTxn = txnFrom(CONTRACT_DELETE_MISSING_CONTRACT_BENEFICIARY_SCENARIO);
    //        final var meta =
    //                subject.preHandle(theTxn, theTxn.transactionID().accountID(), keyLookup);
    //
    //        assertEquals(sanityRestored(meta.payerKey()), DEFAULT_PAYER_KT.asKey());
    //        assertThat(sanityRestored(meta.requiredNonPayerKeys()),
    // contains(MISC_ADMIN_KT.asKey()));
    //        assertEquals(INVALID_CONTRACT_ID, meta.status());
    //    }
    //
    //    @Test
    //    void getsContractDeleteContractXfer() {
    //        final var theTxn = txnFrom(CONTRACT_DELETE_XFER_CONTRACT_SCENARIO);
    //        final var meta =
    //                subject.preHandle(theTxn, theTxn.transactionID().accountID(), keyLookup);
    //
    //        assertEquals(sanityRestored(meta.payerKey()), DEFAULT_PAYER_KT.asKey());
    //        assertThat(
    //                sanityRestored(meta.requiredNonPayerKeys()),
    //                contains(MISC_ADMIN_KT.asKey(), DILIGENT_SIGNING_PAYER_KT.asKey()));
    //        assertEquals(OK, meta.status());
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
