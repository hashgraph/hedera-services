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

import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.scope.ActiveContractVerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleSystemContractScope;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HandleSystemContractScopeTest {
    @Mock
    private HandleContext context;

    private HandleSystemContractScope subject;

    @BeforeEach
    void setUp() {
        subject = new HandleSystemContractScope(context);
    }

    @Test
    void getNftNotImplementedYet() {
        assertThrows(
                AssertionError.class,
                () -> subject.getNftAndExternalizeResult(NftID.DEFAULT, 1L, entity -> Bytes.EMPTY));
    }

    @Test
    void getTokenNotImplementedYet() {
        assertThrows(AssertionError.class, () -> subject.getTokenAndExternalizeResult(1L, 2L, entity -> Bytes.EMPTY));
    }

    @Test
    void getAccountNotImplementedYet() {
        assertThrows(AssertionError.class, () -> subject.getAccountAndExternalizeResult(1L, 2L, entity -> Bytes.EMPTY));
    }

    @Test
    void getRelationshipNotImplementedYet() {
        assertThrows(
                AssertionError.class,
                () -> subject.getRelationshipAndExternalizeResult(1L, 2L, 3L, entity -> Bytes.EMPTY));
    }

    @Test
    void dispatchNotImplementedYet() {
        assertThrows(
                AssertionError.class,
                () -> subject.dispatch(
                        TransactionBody.DEFAULT, new ActiveContractVerificationStrategy(1L, Bytes.EMPTY, false)));
    }
}
