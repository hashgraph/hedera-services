// SPDX-License-Identifier: Apache-2.0
package com.hedera.services.bdd.junit.support.translators.impl;

import static com.hedera.services.bdd.junit.support.translators.impl.ContractCallTranslator.isClprDispatch;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.contract.EvmTransactionResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for {@link ContractCallTranslator}.
 */
class ContractCallTranslatorTest {
    private static final ContractID APPLICATION =
            ContractID.newBuilder().contractNum(1001L).build();
    private static final ContractID HTS =
            ContractID.newBuilder().contractNum(0x167L).build();

    @Test
    @DisplayName("A call a CLPR transaction dispatched carries no sender, so it is a CLPR dispatch")
    void dispatchedCallIsClprDispatch() {
        final var dispatched =
                EvmTransactionResult.newBuilder().contractId(APPLICATION).build();

        assertTrue(isClprDispatch(true, dispatched));
    }

    @Test
    @DisplayName("An HTS view called by a CLPR callback carries its caller as sender, so it is not a CLPR dispatch")
    void systemContractChildOfCallbackIsNotClprDispatch() {
        // E.g. the application callback calling HTS isToken(): a synthetic child CONTRACT_CALL whose result names
        // the calling contract as sender; its receipt must not get the dispatched call's contract id
        final var htsView = EvmTransactionResult.newBuilder()
                .contractId(HTS)
                .senderId(AccountID.newBuilder().accountNum(1001L).build())
                .build();

        assertFalse(isClprDispatch(true, htsView));
    }

    @Test
    @DisplayName("Outside a unit with a CLPR transaction, no child call is a CLPR dispatch")
    void noClprTransactionNoDispatch() {
        final var call =
                EvmTransactionResult.newBuilder().contractId(APPLICATION).build();

        assertFalse(isClprDispatch(false, call));
    }
}
