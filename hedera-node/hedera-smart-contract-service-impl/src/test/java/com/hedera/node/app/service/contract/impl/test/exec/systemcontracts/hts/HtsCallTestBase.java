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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.HederaOperations;
import com.hedera.node.app.service.contract.impl.exec.scope.SystemContractOperations;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
public class HtsCallTestBase {
    @Mock
    protected HederaOperations operations;

    @Mock
    protected HederaNativeOperations nativeOperations;

    @Mock
    protected SystemContractOperations systemContractOperations;

    @Mock
    protected SystemContractGasCalculator gasCalculator;

    protected HederaWorldUpdater.Enhancement mockEnhancement() {
        return new HederaWorldUpdater.Enhancement(operations, nativeOperations, systemContractOperations);
    }

    protected MessageFrame mockMessageFrame() {
        final var mockFrame = mock(MessageFrame.class, withSettings().strictness(Strictness.LENIENT));
        final var updater = mock(ProxyWorldUpdater.class);
        given(mockFrame.getWorldUpdater()).willReturn(updater);
        return mockFrame;
    }
}
