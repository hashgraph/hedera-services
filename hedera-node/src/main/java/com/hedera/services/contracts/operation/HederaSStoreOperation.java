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

import static com.hedera.services.contracts.operation.HederaOperationUtil.cacheExistingValue;
import static org.hyperledger.besu.evm.frame.ExceptionalHaltReason.ILLEGAL_STATE_CHANGE;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.contracts.gascalculator.GasCalculatorHederaV18;
import com.hedera.services.contracts.gascalculator.StorageGasCalculator;
import com.hedera.services.store.contracts.HederaWorldUpdater;
import com.hedera.services.stream.proto.SidecarType;
import java.util.Optional;
import java.util.OptionalLong;
import javax.inject.Inject;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.account.MutableAccount;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.operation.AbstractOperation;
import org.hyperledger.besu.evm.operation.Operation;

/**
 * Hedera adapted version of the {@link org.hyperledger.besu.evm.operation.SStoreOperation}. Gas
 * costs are based on the expiry of the current or parent account and the provided storage bytes per
 * hour variable
 */
public class HederaSStoreOperation extends AbstractOperation {
    private static final Operation.OperationResult ILLEGAL_STATE_CHANGE_RESULT =
            new Operation.OperationResult(OptionalLong.empty(), Optional.of(ILLEGAL_STATE_CHANGE));

    private final boolean checkSuperCost;
    private final StorageGasCalculator storageGasCalculator;
    private final GlobalDynamicProperties dynamicProperties;

    @Inject
    public HederaSStoreOperation(
            final GasCalculator gasCalculator,
            final StorageGasCalculator storageGasCalculator,
            final GlobalDynamicProperties dynamicProperties) {
        super(0x55, "SSTORE", 2, 0, 1, gasCalculator);
        checkSuperCost = !(gasCalculator instanceof GasCalculatorHederaV18);
        this.dynamicProperties = dynamicProperties;
        this.storageGasCalculator = storageGasCalculator;
    }

    @Override
    public Operation.OperationResult execute(final MessageFrame frame, final EVM evm) {
        final UInt256 key = UInt256.fromBytes(frame.popStackItem());
        final UInt256 value = UInt256.fromBytes(frame.popStackItem());

        final MutableAccount account =
                frame.getWorldUpdater().getAccount(frame.getRecipientAddress()).getMutable();
        if (account == null) {
            return ILLEGAL_STATE_CHANGE_RESULT;
        }

        UInt256 currentValue = account.getStorageValue(key);
        boolean currentZero = currentValue.isZero();
        boolean newZero = value.isZero();
        boolean checkCalculator = checkSuperCost;
        long gasCost = 0L;
        if (currentZero && !newZero) {
            gasCost = storageGasCalculator.gasCostOfStorageIn(frame);
            ((HederaWorldUpdater) frame.getWorldUpdater()).addSbhRefund(gasCost);
        } else {
            checkCalculator = true;
        }

        if (checkCalculator) {
            final var address = account.getAddress();
            final var slotIsWarm = frame.warmUpStorage(address, key);
            final var calculator = gasCalculator();
            final var calcGasCost =
                    calculator.calculateStorageCost(account, key, value)
                            + (slotIsWarm ? 0L : calculator.getColdSloadCost());
            gasCost = Math.max(gasCost, calcGasCost);
            frame.incrementGasRefund(
                    gasCalculator().calculateStorageRefundAmount(account, key, value));
        }

        final var optionalCost = OptionalLong.of(gasCost);
        final long remainingGas = frame.getRemainingGas();
        if (frame.isStatic()) {
            return new OperationResult(optionalCost, Optional.of(ILLEGAL_STATE_CHANGE));
        } else if (remainingGas < gasCost) {
            return new OperationResult(
                    optionalCost, Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
        }

        if (dynamicProperties.enabledSidecars().contains(SidecarType.CONTRACT_STATE_CHANGE)) {
            cacheExistingValue(frame, account.getAddress(), key, currentValue);
        }

        account.setStorageValue(key, value);
        frame.storageWasUpdated(key, value);
        return new Operation.OperationResult(optionalCost, Optional.empty());
    }
}
