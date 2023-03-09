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

package com.hedera.node.app.service.evm.contracts.operations;

import com.hedera.node.app.service.evm.contracts.execution.EvmProperties;
import com.hedera.node.app.service.evm.store.contracts.HederaEvmWorldUpdater;
import javax.inject.Inject;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

public class HederaEvmCreateOperation extends AbstractEvmRecordingCreateOperation {
    @Inject
    public HederaEvmCreateOperation(
            final GasCalculator gasCalculator,
            final EvmProperties evmProperties,
            final CreateOperationExternalizer createOperationExternalizer) {
        super(0xF0, "ħCREATE", 3, 1, 1, gasCalculator, evmProperties, createOperationExternalizer);
    }

    @Override
    public long cost(final MessageFrame frame) {
        return gasCalculator().createOperationGasCost(frame);
    }

    @Override
    protected boolean isEnabled() {
        return true;
    }

    @Override
    protected Address targetContractAddress(MessageFrame frame) {
        final var updater = (HederaEvmWorldUpdater) frame.getWorldUpdater();
        final Address address = updater.newContractAddress(frame.getRecipientAddress());
        frame.warmUpAddress(address);
        return address;
    }
}
