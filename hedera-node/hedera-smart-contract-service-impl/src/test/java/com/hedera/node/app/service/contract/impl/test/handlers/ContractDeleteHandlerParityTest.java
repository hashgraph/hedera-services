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

import static com.hedera.test.factories.scenarios.ContractCreateScenarios.DILIGENT_SIGNING_PAYER_KT;
import static com.hedera.test.factories.scenarios.ContractCreateScenarios.MISC_ADMIN_KT;
import static com.hedera.test.factories.scenarios.ContractCreateScenarios.RECEIVER_SIG_KT;
import static com.hedera.test.factories.scenarios.ContractDeleteScenarios.*;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;
import static com.hedera.test.utils.KeyUtils.sanityRestored;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.node.app.service.contract.impl.handlers.ContractDeleteHandler;
import com.hedera.node.app.spi.accounts.AccountAccess;
import com.hedera.node.app.spi.meta.PreHandleContext;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ContractDeleteHandlerParityTest {
    private AccountAccess keyLookup;
    private final ContractDeleteHandler subject = new ContractDeleteHandler();

    @BeforeEach
    void setUp() {
        keyLookup = AdapterUtils.wellKnownKeyLookupAt();
    }

    @Test
    void getsContractDeleteImmutable() {
        final var theTxn = txnFrom(CONTRACT_DELETE_IMMUTABLE_SCENARIO);
        final var context = new PreHandleContext(keyLookup, theTxn);
        subject.preHandle(context);

        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
        assertTrue(sanityRestored(context.getRequiredNonPayerKeys()).isEmpty());
        assertEquals(MODIFYING_IMMUTABLE_CONTRACT, context.getStatus());
    }

    @Test
    void getsContractDelete() {
        final var theTxn = txnFrom(CONTRACT_DELETE_XFER_ACCOUNT_SCENARIO);
        final var context = new PreHandleContext(keyLookup, theTxn);
        subject.preHandle(context);

        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
        assertThat(
                sanityRestored(context.getRequiredNonPayerKeys()),
                contains(MISC_ADMIN_KT.asKey(), RECEIVER_SIG_KT.asKey()));
    }

    @Test
    void getsContractDeleteMissingAccountBeneficiary() {
        final var theTxn = txnFrom(CONTRACT_DELETE_MISSING_ACCOUNT_BENEFICIARY_SCENARIO);
        final var context = new PreHandleContext(keyLookup, theTxn);
        subject.preHandle(context);

        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
        assertThat(sanityRestored(context.getRequiredNonPayerKeys()), contains(MISC_ADMIN_KT.asKey()));
        assertEquals(INVALID_TRANSFER_ACCOUNT_ID, context.getStatus());
    }

    @Test
    void getsContractDeleteMissingContractBeneficiary() {
        final var theTxn = txnFrom(CONTRACT_DELETE_MISSING_CONTRACT_BENEFICIARY_SCENARIO);
        final var context = new PreHandleContext(keyLookup, theTxn);
        subject.preHandle(context);

        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
        assertThat(sanityRestored(context.getRequiredNonPayerKeys()), contains(MISC_ADMIN_KT.asKey()));
        assertEquals(INVALID_CONTRACT_ID, context.getStatus());
    }

    @Test
    void getsContractDeleteContractXfer() {
        final var theTxn = txnFrom(CONTRACT_DELETE_XFER_CONTRACT_SCENARIO);
        final var context = new PreHandleContext(keyLookup, theTxn);
        subject.preHandle(context);

        assertEquals(sanityRestored(context.getPayerKey()), DEFAULT_PAYER_KT.asKey());
        assertThat(
                sanityRestored(context.getRequiredNonPayerKeys()),
                contains(MISC_ADMIN_KT.asKey(), DILIGENT_SIGNING_PAYER_KT.asKey()));
        assertEquals(OK, context.getStatus());
    }

    private TransactionBody txnFrom(final TxnHandlingScenario scenario) {
        try {
            return scenario.platformTxn().getTxn();
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
