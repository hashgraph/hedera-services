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
import com.hedera.services.evm.contracts.operations.HederaEvmSLoadOperation;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.stream.proto.SidecarType;
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

import javax.inject.Inject;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Hedera adapted version of the {@link org.hyperledger.besu.evm.operation.SLoadOperation}. No
 * externally visible changes, the result of sload is stored for the benefit of ther record stream.
 */
public class HederaSLoadOperation extends HederaEvmSLoadOperation {
    private final GlobalDynamicProperties dynamicProperties;

    @Inject
    public HederaSLoadOperation(
            final GasCalculator gasCalculator, final GlobalDynamicProperties dynamicProperties) {
        super(gasCalculator);
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
            final OptionalLong optionalCost = slotIsWarm ? warmCost : coldCost;
            if (frame.getRemainingGas() < optionalCost.orElse(0L)) {
                return new OperationResult(
                        optionalCost, Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
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
            return new OperationResult(
                    warmCost, Optional.of(ExceptionalHaltReason.INSUFFICIENT_STACK_ITEMS));
        } catch (final OverflowException ofe) {
            return new OperationResult(
                    warmCost, Optional.of(ExceptionalHaltReason.TOO_MANY_STACK_ITEMS));
        }
    }
}
