/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.evm.contracts.operations;

import com.hedera.services.evm.store.contracts.AbstractLedgerEvmWorldUpdater;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.FixedStack;
import org.hyperledger.besu.evm.operation.AbstractOperation;

public class HederaEvmSLoadOperation extends AbstractOperation {
    protected final long warmCost;
    protected final long coldCost;

    protected final OperationResult warmSuccess;
    protected final OperationResult coldSuccess;

    protected HederaEvmSLoadOperation(final GasCalculator gasCalculator) {
        super(0x54, "SLOAD", 1, 1, 1, gasCalculator);
        final long baseCost = gasCalculator.getSloadOperationGasCost();
        warmCost = baseCost + gasCalculator.getWarmStorageReadCost();
        coldCost = baseCost + gasCalculator.getColdSloadCost();

        warmSuccess = new OperationResult(warmCost, null);
        coldSuccess = new OperationResult(coldCost, null);
    }

    @Override
    public OperationResult execute(MessageFrame frame, EVM evm) {
        try {
            final var addressOrAlias = frame.getRecipientAddress();
            final var worldUpdater = (AbstractLedgerEvmWorldUpdater) frame.getWorldUpdater();
            final Account account = worldUpdater.get(addressOrAlias);
            final Address address = account.getAddress();
            final Bytes32 key = UInt256.fromBytes(frame.popStackItem());
            final boolean slotIsWarm = frame.warmUpStorage(address, key);
            final long optionalCost = slotIsWarm ? warmCost : coldCost;
            if (frame.getRemainingGas() < optionalCost) {
                return new OperationResult(optionalCost, ExceptionalHaltReason.INSUFFICIENT_GAS);
            } else {
                UInt256 storageValue = account.getStorageValue(UInt256.fromBytes(key));

                intermediateCustomLogic(frame, address, key, storageValue);

                frame.pushStackItem(storageValue);
                return slotIsWarm ? warmSuccess : coldSuccess;
            }
        } catch (final FixedStack.UnderflowException ufe) {
            return new OperationResult(warmCost, ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);
        } catch (final FixedStack.OverflowException ofe) {
            return new OperationResult(warmCost, ExceptionalHaltReason.TOO_MANY_STACK_ITEMS);
        }
    }

    /**
     * A placeholder logic method to be overridden by {@link HederaEvmSLoadOperation} descendants.
     */
    protected void intermediateCustomLogic(
            MessageFrame frame, Address address, Bytes32 key, UInt256 storageValue) {
        // The method is intentionally-blank
    }
}
