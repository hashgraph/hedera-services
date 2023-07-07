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

package com.hedera.node.app.service.contract.impl.test.hevm;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.*;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.pbjToTuweniBytes;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmTransactionResult;
import com.hedera.node.app.service.contract.impl.infra.StorageAccessTracker;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import java.util.List;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HederaEvmTransactionResultTest {
    @Mock
    private MessageFrame frame;

    @Mock
    private ProxyWorldUpdater proxyWorldUpdater;

    @Mock
    private StorageAccessTracker accessTracker;

    @Test
    void abortsWithTranslatedStatus() {
        var subject = HederaEvmTransactionResult.abortFor(ResponseCodeEnum.INVALID_SIGNATURE);

        assertEquals(ResponseCodeEnum.INVALID_SIGNATURE, subject.abortReason());
    }

    @Test
    void givenAccessTrackerIncludesFullContractStorageChangesOnSuccess() {
        given(frame.getContextVariable(FrameUtils.TRACKER_CONTEXT_VARIABLE)).willReturn(accessTracker);
        given(frame.getWorldUpdater()).willReturn(proxyWorldUpdater);
        final var pendingWrites = List.of(TWO_STORAGE_ACCESSES);
        given(proxyWorldUpdater.pendingStorageUpdates()).willReturn(pendingWrites);
        given(accessTracker.getReadsMergedWith(pendingWrites)).willReturn(SOME_STORAGE_ACCESSES);
        given(frame.getGasPrice()).willReturn(WEI_NETWORK_GAS_PRICE);
        given(frame.getOutputData()).willReturn(pbjToTuweniBytes(OUTPUT_DATA));

        final var result = HederaEvmTransactionResult.successFrom(
                GAS_LIMIT / 2, CALLED_CONTRACT_ID, CALLED_CONTRACT_EVM_ADDRESS, frame);

        final var expectedChanges = ConversionUtils.asPbjStateChanges(SOME_STORAGE_ACCESSES);
        assertEquals(expectedChanges, result.stateChanges());
    }

    @Test
    void givenAccessTrackerIncludesReadStorageAccessesOnlyOnFailure() {
        given(frame.getContextVariable(FrameUtils.TRACKER_CONTEXT_VARIABLE)).willReturn(accessTracker);
        given(accessTracker.getJustReads()).willReturn(SOME_STORAGE_ACCESSES);
        given(frame.getGasPrice()).willReturn(WEI_NETWORK_GAS_PRICE);

        final var result = HederaEvmTransactionResult.failureFrom(GAS_LIMIT / 2, frame);

        final var expectedChanges = ConversionUtils.asPbjStateChanges(SOME_STORAGE_ACCESSES);
        assertEquals(expectedChanges, result.stateChanges());
    }

    @Test
    void withoutAccessTrackerReturnsNullStateChanges() {
        given(frame.getGasPrice()).willReturn(WEI_NETWORK_GAS_PRICE);
        given(frame.getOutputData()).willReturn(pbjToTuweniBytes(OUTPUT_DATA));

        final var result = HederaEvmTransactionResult.successFrom(
                GAS_LIMIT / 2, CALLED_CONTRACT_ID, CALLED_CONTRACT_EVM_ADDRESS, frame);

        assertNull(result.stateChanges());
    }
}
