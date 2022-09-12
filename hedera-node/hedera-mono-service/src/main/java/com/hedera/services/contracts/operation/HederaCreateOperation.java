/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.contracts.operation;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.records.RecordsHistorian;
import com.hedera.services.state.EntityCreator;
import com.hedera.services.store.contracts.HederaWorldUpdater;
import com.hedera.services.store.contracts.precompile.SyntheticTxnFactory;
import javax.inject.Inject;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

/**
 * Hedera adapted version of the {@link org.hyperledger.besu.evm.operation.CreateOperation}.
 *
 * <p>Addresses are allocated through {@link HederaWorldUpdater#newContractAddress(Address)}
 *
 * <p>Gas costs are based on the expiry of the parent and the provided storage bytes per hour
 * variable
 */
public class HederaCreateOperation extends AbstractRecordingCreateOperation {
    @Inject
    public HederaCreateOperation(
            final GasCalculator gasCalculator,
            final EntityCreator creator,
            final SyntheticTxnFactory syntheticTxnFactory,
            final RecordsHistorian recordsHistorian,
            final GlobalDynamicProperties dynamicProperties) {
        super(
                0xF0,
                "ħCREATE",
                3,
                1,
                1,
                gasCalculator,
                creator,
                syntheticTxnFactory,
                recordsHistorian,
                dynamicProperties);
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
    protected Address targetContractAddress(final MessageFrame frame) {
        final var updater = (HederaWorldUpdater) frame.getWorldUpdater();
        final Address address = updater.newContractAddress(frame.getRecipientAddress());
        frame.warmUpAddress(address);
        return address;
    }
}
