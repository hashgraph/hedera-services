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
package com.hedera.services.ledger;

import static com.hedera.services.exceptions.InsufficientFundsException.messageFor;
import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

import com.hedera.services.exceptions.DeletedAccountException;
import com.hedera.services.exceptions.InsufficientFundsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HederaLedgerXfersTest extends BaseHederaLedgerTestHelper {
    @BeforeEach
    void setup() {
        commonSetup();
        setupWithMockLedger();
    }

    @Test
    void throwsOnTransferWithDeletedFromAccount() {
        final var e =
                assertThrows(
                        DeletedAccountException.class, () -> subject.doTransfer(deleted, misc, 1L));

        assertEquals("0.0.3456", e.getMessage());
        verify(accountsLedger, never()).set(any(), any(), any());
    }

    @Test
    void throwsOnTransferWithDeletedToAccount() {
        final var e =
                assertThrows(
                        DeletedAccountException.class, () -> subject.doTransfer(misc, deleted, 1L));

        assertEquals("0.0.3456", e.getMessage());
        verify(accountsLedger, never()).set(any(), any(), any());
    }

    @Test
    void doesReasonableTransfer() {
        final var amount = 1_234L;

        subject.doTransfer(genesis, misc, amount);

        verify(accountsLedger).set(genesis, BALANCE, GENESIS_BALANCE - amount);
        verify(accountsLedger).set(misc, BALANCE, MISC_BALANCE + amount);
    }

    @Test
    void throwsOnImpossibleTransferWithBrokerPayer() {
        final var amount = GENESIS_BALANCE + 1;
        final var e =
                assertThrows(
                        InsufficientFundsException.class,
                        () -> subject.doTransfer(genesis, misc, amount));

        assertEquals(messageFor(genesis, -1 * amount), e.getMessage());
        verify(accountsLedger, never()).set(any(), any(), any());
    }
}
