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

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.node.app.service.contract.impl.exec.scope.ActiveContractVerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleHederaNativeOperations;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HandleHederaNativeOperationsTest {
    @Mock
    private HandleContext context;

    private HandleHederaNativeOperations subject;

    @BeforeEach
    void setUp() {
        subject = new HandleHederaNativeOperations(context);
    }

    @Test
    void getAccountNotImplemented() {
        assertThrows(AssertionError.class, () -> subject.getAccount(1L));
    }

    @Test
    void getTokenNotImplemented() {
        assertThrows(AssertionError.class, () -> subject.getToken(1L));
    }

    @Test
    void resolveAliasNotImplemented() {
        assertThrows(AssertionError.class, () -> subject.resolveAlias(Bytes.EMPTY));
    }

    @Test
    void setNonceNotImplemented() {
        assertThrows(AssertionError.class, () -> subject.setNonce(1L, 2L));
    }

    @Test
    void createHollowAccountNotImplemented() {
        assertThrows(AssertionError.class, () -> subject.createHollowAccount(Bytes.EMPTY));
    }

    @Test
    void finalizeHollowAccountAsContractNotImplemented() {
        assertThrows(AssertionError.class, () -> subject.finalizeHollowAccountAsContract(Bytes.EMPTY));
    }

    @Test
    void collectFeeNotImplemented() {
        assertThrows(AssertionError.class, () -> subject.collectFee(1L, 2L));
    }

    @Test
    void refundFeeNotImplemented() {
        assertThrows(AssertionError.class, () -> subject.refundFee(1L, 2L));
    }

    @Test
    void transferWithReceiverSigCheckNotImplemented() {
        assertThrows(
                AssertionError.class,
                () -> subject.transferWithReceiverSigCheck(
                        1L, 2L, 3L, new ActiveContractVerificationStrategy(4L, Bytes.EMPTY, false)));
    }

    @Test
    void trackDeletionNotImplemented() {
        assertThrows(AssertionError.class, () -> subject.trackDeletion(1L, 2L));
    }
}
