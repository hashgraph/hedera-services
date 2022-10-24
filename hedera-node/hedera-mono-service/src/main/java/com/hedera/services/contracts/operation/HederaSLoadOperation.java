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
package com.hedera.services.contracts.operation;

/*
 * -
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 *
 */

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.stream.proto.SidecarType;
import javax.inject.Inject;
import org.apache.tuweni.bytes.Bytes32;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.FixedStack.OverflowException;
import org.hyperledger.besu.evm.internal.FixedStack.UnderflowException;
import org.hyperledger.besu.evm.operation.AbstractOperation;

/**
 * Hedera adapted version of the {@link org.hyperledger.besu.evm.operation.SLoadOperation}. No
 * externally visible changes, the result of sload is stored for the benefit of their record stream.
 */
public class HederaSLoadOperation extends AbstractOperation {

    private final long warmCost;
    private final long coldCost;

    private final OperationResult warmSuccess;
    private final OperationResult coldSuccess;
    private final GlobalDynamicProperties dynamicProperties;

    @Inject
    public HederaSLoadOperation(
            final GasCalculator gasCalculator, final GlobalDynamicProperties dynamicProperties) {
        super(0x54, "SLOAD", 1, 1, 1, gasCalculator);
        final long baseCost = gasCalculator.getSloadOperationGasCost();
        warmCost = baseCost + gasCalculator.getWarmStorageReadCost();
        coldCost = baseCost + gasCalculator.getColdSloadCost();

        warmSuccess = new OperationResult(warmCost, null);
        coldSuccess = new OperationResult(coldCost, null);
        this.dynamicProperties = dynamicProperties;
    }

    @Override
    public OperationResult execute(final MessageFrame frame, final EVM evm) {
        try {
            final var addressOrAlias = frame.getRecipientAddress();
            final var worldUpdater = (HederaStackedWorldStateUpdater) frame.getWorldUpdater();
            final Account account = worldUpdater.get(addressOrAlias);
            final Address address = account.getAddress();
            final Bytes32 key = UInt256.fromBytes(frame.popStackItem());
            final boolean slotIsWarm = frame.warmUpStorage(address, key);
            final long optionalCost = slotIsWarm ? warmCost : coldCost;
            if (frame.getRemainingGas() < optionalCost) {
                return new OperationResult(optionalCost, ExceptionalHaltReason.INSUFFICIENT_GAS);
            } else {
                UInt256 storageValue = account.getStorageValue(UInt256.fromBytes(key));
                if (dynamicProperties
                        .enabledSidecars()
                        .contains(SidecarType.CONTRACT_STATE_CHANGE)) {
                    HederaOperationUtil.cacheExistingValue(frame, address, key, storageValue);
                }

                frame.pushStackItem(storageValue);
                return slotIsWarm ? warmSuccess : coldSuccess;
            }
        } catch (final UnderflowException ufe) {
            return new OperationResult(warmCost, ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS);
        } catch (final OverflowException ofe) {
            return new OperationResult(warmCost, ExceptionalHaltReason.TOO_MANY_STACK_ITEMS);
        }
    }
}
