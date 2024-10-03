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

package com.hedera.node.app.service.contract.impl.exec.operations;

import static com.hedera.node.app.service.contract.impl.exec.operations.CustomizedOpcodes.CREATE;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

public class CustomCreateOperation extends AbstractCustomCreateOperation {
    public CustomCreateOperation(@NonNull final GasCalculator gasCalculator) {
        super(CREATE.opcode(), "ħCREATE", 3, 1, gasCalculator);
    }

    @Override
    protected boolean isEnabled(@NonNull final MessageFrame frame) {
        return true;
    }

    @Override
    protected void onSuccess(@NonNull final MessageFrame frame, @NonNull final Address createdAddress) {
        // Nothing to do here, the record of the creation will be tracked as
        // a side effect of dispatching from ProxyWorldUpdater#createAccount()
    }

    @Override
    protected long cost(@NonNull final MessageFrame frame) {
        return gasCalculator().createOperationGasCost(frame);
    }

    @Override
    protected @NonNull Address setupPendingCreation(@NonNull final MessageFrame frame) {
        final var updater = (ProxyWorldUpdater) frame.getWorldUpdater();

        final var origin = frame.getRecipientAddress();
        final var originNonce = requireNonNull(updater.getAccount(origin)).getNonce();
        // Decrement nonce by 1 to normalize the effect of transaction execution
        final var address = Address.contractAddress(origin, originNonce - 1);

        updater.setupInternalAliasedCreate(origin, address);
        frame.warmUpAddress(address);
        return address;
    }
}
