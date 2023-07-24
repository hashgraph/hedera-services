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

import com.hedera.node.app.service.contract.impl.exec.scope.HandleExtWorldScope;
import com.hedera.node.app.service.contract.impl.state.WritableContractStateStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HandleExtWorldScopeTest {
    @Mock
    private HandleContext.SavepointStack savepointStack;

    @Mock
    private HandleContext context;

    @Mock
    private WritableContractStateStore stateStore;

    private HandleExtWorldScope subject;

    @BeforeEach
    void setUp() {
        subject = new HandleExtWorldScope(context);
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
    void commitIsUnsupportedForNow() {
        assertThrows(UnsupportedOperationException.class, subject::commit);
    }
}
