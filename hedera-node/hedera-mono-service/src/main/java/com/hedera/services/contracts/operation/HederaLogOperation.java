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

import static org.apache.tuweni.bytes.Bytes32.leftPad;
import static org.hyperledger.besu.evm.internal.Words.clampedToLong;

import com.google.common.collect.ImmutableList;
import com.hedera.services.store.contracts.HederaStackedWorldStateUpdater;
import com.hedera.services.utils.EntityNum;
import java.util.Optional;
import java.util.OptionalLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogTopic;
import org.hyperledger.besu.evm.operation.AbstractOperation;

public class HederaLogOperation extends AbstractOperation {
    private static final Logger log = LogManager.getLogger(HederaLogOperation.class);

    private static final Address UNRESOLVABLE_ADDRESS_STANDIN =
            EntityNum.MISSING_NUM.toEvmAddress();

    private final int numTopics;

    public HederaLogOperation(final int numTopics, final GasCalculator gasCalculator) {
        super(0xA0 + numTopics, "LOG" + numTopics, numTopics + 2, 0, 1, gasCalculator);
        this.numTopics = numTopics;
    }

    @Override
    public OperationResult execute(final MessageFrame frame, final EVM evm) {
        final long dataLocation = clampedToLong(frame.popStackItem());
        final long numBytes = clampedToLong(frame.popStackItem());

        final long cost =
                gasCalculator().logOperationGasCost(frame, dataLocation, numBytes, numTopics);
        final OptionalLong optionalCost = OptionalLong.of(cost);
        if (frame.isStatic()) {
            return new OperationResult(
                    optionalCost, Optional.of(ExceptionalHaltReason.ILLEGAL_STATE_CHANGE));
        } else if (frame.getRemainingGas() < cost) {
            return new OperationResult(
                    optionalCost, Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS));
        }

        final var addressOrAlias = frame.getRecipientAddress();
        final var updater = (HederaStackedWorldStateUpdater) frame.getWorldUpdater();
        final var aliases = updater.aliases();
        var address = aliases.resolveForEvm(addressOrAlias);
        if (!aliases.isMirror(address)) {
            address = UNRESOLVABLE_ADDRESS_STANDIN;
            log.warn("Could not resolve logger address {}", addressOrAlias);
        }

        final Bytes data = frame.readMemory(dataLocation, numBytes);

        final ImmutableList.Builder<LogTopic> builder =
                ImmutableList.builderWithExpectedSize(numTopics);
        for (int i = 0; i < numTopics; i++) {
            builder.add(LogTopic.create(leftPad(frame.popStackItem())));
        }

        frame.addLog(new Log(address, data, builder.build()));
        return new OperationResult(optionalCost, Optional.empty());
    }
}
