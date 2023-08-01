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

package com.hedera.node.app.service.contract.impl.test.exec.scope;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.service.contract.impl.exec.scope.QueryHederaOperations;
import com.hedera.node.app.service.contract.impl.state.ContractStateStore;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QueryHederaOperationsTest {
    @Mock
    private QueryContext context;

    @Mock
    private ContractStateStore store;

    private QueryHederaOperations subject;

    @BeforeEach
    void setUp() {
        subject = new QueryHederaOperations(context);
    }

    @Test
    void beginningNewExtWorldScopeReturnsThis() {
        assertSame(subject, subject.begin());
    }

    @Test
    void commitIsNoop() {
        assertDoesNotThrow(subject::commit);
    }

    @Test
    void revertIsNoop() {
        assertDoesNotThrow(subject::revert);
    }

    @Test
    void createsStoreAsExpected() {
        given(context.createStore(ContractStateStore.class)).willReturn(store);

        assertSame(store, subject.getStore());
    }

    @Test
    void peekingAndUsingEntityNumbersNotSupported() {
        assertThrows(UnsupportedOperationException.class, subject::peekNextEntityNumber);
        assertThrows(UnsupportedOperationException.class, subject::useNextEntityNumber);
    }

    @Test
    void entropyNotYetImplemented() {
        assertThrows(AssertionError.class, subject::entropy);
    }

    @Test
    void gasPriceNotYetImplemented() {
        assertThrows(AssertionError.class, subject::gasPriceInTinybars);
    }

    @Test
    void tinybarConversionNotYetImplemented() {
        assertThrows(AssertionError.class, () -> subject.valueInTinybars(1234L));
    }

    @Test
    void collectingAndRefundingFeesNotSupported() {
        assertThrows(UnsupportedOperationException.class, () -> subject.collectFee(AccountID.DEFAULT, 1234L));
        assertThrows(UnsupportedOperationException.class, () -> subject.refundFee(AccountID.DEFAULT, 1234L));
    }

    @Test
    void chargingStorageRentNotSupported() {
        assertThrows(UnsupportedOperationException.class, () -> subject.chargeStorageRent(1L, 2L, true));
    }

    @Test
    void creatingAndDeletingContractsNotSupported() {
        assertThrows(UnsupportedOperationException.class, () -> subject.createContract(1L, 2L, 3L, null));
        assertThrows(UnsupportedOperationException.class, () -> subject.deleteAliasedContract(Bytes.EMPTY));
        assertThrows(UnsupportedOperationException.class, () -> subject.deleteUnaliasedContract(1234L));
    }

    @Test
    void neverAnyModifiedAccountNumbers() {
        assertSame(Collections.emptyList(), subject.getModifiedAccountNumbers());
    }

    @Test
    void neverAnyCreatedContractIds() {
        assertSame(Collections.emptyList(), subject.createdContractIds());
    }

    @Test
    void neverAnyUpdatedNonces() {
        assertSame(Collections.emptyList(), subject.updatedContractNonces());
    }

    @Test
    void getOriginalSlotsUsedNotSupported() {
        assertThrows(UnsupportedOperationException.class, () -> subject.getOriginalSlotsUsed(1234L));
    }
}
