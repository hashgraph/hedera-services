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

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleHederaOperations;
import com.hedera.node.app.service.contract.impl.state.WritableContractStateStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HandleHederaOperationsTest {
    @Mock
    private HandleContext.SavepointStack savepointStack;

    @Mock
    private HandleContext context;

    @Mock
    private WritableContractStateStore stateStore;

    private HandleHederaOperations subject;

    @BeforeEach
    void setUp() {
        subject = new HandleHederaOperations(context);
    }

    @Test
    void returnsContextualStore() {
        given(context.writableStore(WritableContractStateStore.class)).willReturn(stateStore);

        assertSame(stateStore, subject.getStore());
    }

    @Test
    void createsNewSavepointWhenBeginningScope() {
        given(context.savepointStack()).willReturn(savepointStack);

        final var nestedScope = subject.begin();

        assertSame(subject, nestedScope);
        verify(savepointStack).createSavepoint();
    }

    @Test
    void rollsBackSavepointWhenReverting() {
        given(context.savepointStack()).willReturn(savepointStack);

        subject.revert();

        verify(savepointStack).rollback();
    }

    @Test
    void commitIsNotImplemented() {
        assertThrows(AssertionError.class, subject::commit);
    }

    @Test
    void lazyCreationCostInGasNotImplemented() {
        assertThrows(AssertionError.class, subject::lazyCreationCostInGas);
    }

    @Test
    void gasPriceInTinybarsNotImplemented() {
        assertThrows(AssertionError.class, subject::gasPriceInTinybars);
    }

    @Test
    void valueInTinybarsNotImplemented() {
        assertThrows(AssertionError.class, () -> subject.valueInTinybars(1L));
    }

    @Test
    void collectFeeNotImplemented() {
        assertThrows(AssertionError.class, () -> subject.collectFee(AccountID.DEFAULT, 1L));
    }

    @Test
    void refundFeeNotImplemented() {
        assertThrows(AssertionError.class, () -> subject.refundFee(AccountID.DEFAULT, 1L));
    }

    @Test
    void chargeStorageRentNotImplemented() {
        assertThrows(AssertionError.class, () -> subject.chargeStorageRent(1L, 2L, true));
    }

    @Test
    void updateStorageMetadataNotImplemented() {
        assertThrows(AssertionError.class, () -> subject.updateStorageMetadata(1L, Bytes.EMPTY, 2));
    }

    @Test
    void createContractNotImplemented() {
        assertThrows(AssertionError.class, () -> subject.createContract(1L, 2L, 3L, Bytes.EMPTY));
    }

    @Test
    void deleteAliasedContractNotImplemented() {
        assertThrows(AssertionError.class, () -> subject.deleteAliasedContract(Bytes.EMPTY));
    }

    @Test
    void deleteUnaliasedContractNotImplemented() {
        assertThrows(AssertionError.class, () -> subject.deleteAliasedContract(Bytes.EMPTY));
    }

    @Test
    void getModifiedAccountNumbersNotImplemented() {
        assertThrows(AssertionError.class, subject::getModifiedAccountNumbers);
    }

    @Test
    void createdContractIdsNotImplemented() {
        assertThrows(AssertionError.class, subject::createdContractIds);
    }

    @Test
    void updatedContractNoncesNotImplemented() {
        assertThrows(AssertionError.class, subject::updatedContractNonces);
    }

    @Test
    void getOriginalSlotsUsedNotImplemented() {
        assertThrows(AssertionError.class, () -> subject.getOriginalSlotsUsed(1L));
    }
}
