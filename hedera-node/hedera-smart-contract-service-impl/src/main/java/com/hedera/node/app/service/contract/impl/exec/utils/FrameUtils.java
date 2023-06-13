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

package com.hedera.node.app.service.contract.impl.exec.utils;

import static com.hedera.node.app.service.contract.impl.exec.TransactionProcessor.CONFIG_CONTEXT_VARIABLE;
import static java.util.Objects.requireNonNull;

import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Predicate;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class FrameUtils {
    public static @NonNull Configuration configOf(@NonNull final MessageFrame frame) {
        return requireNonNull(frame.getContextVariable(CONFIG_CONTEXT_VARIABLE));
    }

    public static boolean testConfigOf(
            @NonNull final MessageFrame frame, @NonNull final Predicate<Configuration> test) {
        return test.test(configOf(frame));
    }

    public static boolean isDelegateCall(@NonNull final MessageFrame frame) {
        return !frame.getRecipientAddress().equals(frame.getContractAddress());
    }

    public static boolean transfersValue(@NonNull final MessageFrame frame) {
        return !frame.getValue().isZero();
    }

    public static boolean alreadyHalted(@NonNull final MessageFrame frame) {
        return frame.getState() == MessageFrame.State.EXCEPTIONAL_HALT;
    }
}
