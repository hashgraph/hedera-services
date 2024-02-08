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

package com.hedera.node.app.service.contract.impl.exec.utils;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Function;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;
import org.hyperledger.besu.evm.internal.Words;

public class OperationUtils {
    public static boolean isDeficientGas(
            @NonNull final MessageFrame frame,
            @NonNull final GasCalculator gasCalculator,
            @NonNull final Function<Boolean, Long> cost) {
        final Address address = Words.toAddress(frame.getStackItem(0));
        final long totalCost = cost.apply(false);
        return frame.getRemainingGas() < totalCost;
    }
}
