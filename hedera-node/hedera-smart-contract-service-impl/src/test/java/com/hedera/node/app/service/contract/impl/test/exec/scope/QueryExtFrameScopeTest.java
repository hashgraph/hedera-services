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
import com.hedera.node.app.service.contract.impl.exec.scope.QueryExtFrameScope;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class QueryExtFrameScopeTest {
    @Mock
    private QueryContext context;

    private QueryExtFrameScope subject;

    @BeforeEach
    void setUp() {
        subject = new QueryExtFrameScope(context);
    }

    @Test
    void doesNotSupportAnyMutations() {
        assertThrows(UnsupportedOperationException.class, () -> subject.setNonce(1L, 2L));
        assertThrows(UnsupportedOperationException.class, () -> subject.createHollowAccount(Bytes.EMPTY));
        assertThrows(UnsupportedOperationException.class, () -> subject.finalizeHollowAccountAsContract(Bytes.EMPTY));
        assertThrows(UnsupportedOperationException.class, () -> subject.finalizeHollowAccountAsContract(Bytes.EMPTY));
        assertThrows(UnsupportedOperationException.class, () -> subject.collectFee(1L, 2L));
        assertThrows(UnsupportedOperationException.class, () -> subject.refundFee(1L, 2L));
        assertThrows(
                UnsupportedOperationException.class,
                () -> subject.transferWithReceiverSigCheck(
                        1L, 2L, 3L, new ActiveContractVerificationStrategy(1, Bytes.EMPTY, true)));
        assertThrows(UnsupportedOperationException.class, () -> subject.trackDeletion(1L, 2L));
    }
}
