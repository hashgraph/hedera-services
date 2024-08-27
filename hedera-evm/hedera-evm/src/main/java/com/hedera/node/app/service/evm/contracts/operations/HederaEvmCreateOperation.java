/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import com.hedera.node.app.service.evm.store.contracts.HederaEvmStackedWorldUpdater;
import javax.inject.Inject;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

/**
 * Hedera adapted version of CreateOperation.
 */
public class HederaEvmCreateOperation extends AbstractEvmRecordingCreateOperation {
    @Inject
    public HederaEvmCreateOperation(
            final GasCalculator gasCalculator, final CreateOperationExternalizer createOperationExternalizer) {
        super(0xF0, "ħCREATE", 3, 1, gasCalculator, createOperationExternalizer);
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
        final var sourceAddressOrAlias = frame.getRecipientAddress();
        final var sourceNonce =
                frame.getWorldUpdater().getAccount(sourceAddressOrAlias).getNonce();
        final var updater = (HederaEvmStackedWorldUpdater) frame.getWorldUpdater();
        // Decrement nonce by 1 to normalize the effect of transaction execution
        final var alias = Address.contractAddress(sourceAddressOrAlias, sourceNonce - 1L);

        final Address address = updater.newAliasedContractAddress(sourceAddressOrAlias, alias);
        frame.warmUpAddress(address);
        frame.warmUpAddress(alias);
        return alias;
    }
}
