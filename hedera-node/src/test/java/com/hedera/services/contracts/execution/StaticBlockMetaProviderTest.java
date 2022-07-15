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
package com.hedera.services.contracts.execution;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

import com.hedera.services.context.StateChildren;
import com.hedera.services.context.primitives.SignedStateViewFactory;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StaticBlockMetaProviderTest {
    @Mock private SignedStateViewFactory stateViewFactory;
    @Mock private MerkleNetworkContext networkContext;
    @Mock private StateChildren stateChildren;

    private StaticBlockMetaProvider subject;

    @BeforeEach
    void setUp() {
        subject = new StaticBlockMetaProvider(stateViewFactory);
    }

    @Test
    void emptyResultIfNoSignedStateChildren() {
        final var result = subject.getSource();
        assertTrue(result.isEmpty());
    }

    @Test
    void usableSourceIfSignedStateChildren() {
        given(stateViewFactory.childrenOfLatestSignedState())
                .willReturn(Optional.of(stateChildren));
        given(stateChildren.networkCtx()).willReturn(networkContext);
        final var result = subject.getSource();
        assertTrue(result.isPresent());
    }
}
