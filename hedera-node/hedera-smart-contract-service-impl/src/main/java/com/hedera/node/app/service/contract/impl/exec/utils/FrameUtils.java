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

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.contract.impl.infra.StorageAccessTracker;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import com.hedera.node.config.data.ContractsConfig;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class FrameUtils {
    public static final String CONFIG_CONTEXT_VARIABLE = "contractsConfig";
    public static final String TRACKER_CONTEXT_VARIABLE = "storageAccessTracker";

    private FrameUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static @NonNull Configuration configOf(@NonNull final MessageFrame frame) {
        return requireNonNull(frame.getContextVariable(CONFIG_CONTEXT_VARIABLE));
    }

    public static @NonNull ContractsConfig contractsConfigOf(@NonNull final MessageFrame frame) {
        return configOf(frame).getConfigData(ContractsConfig.class);
    }

    public static @Nullable StorageAccessTracker accessTrackerFor(@NonNull final MessageFrame frame) {
        return frame.getContextVariable(TRACKER_CONTEXT_VARIABLE);
    }

    public static @NonNull ProxyWorldUpdater proxyUpdaterFor(@NonNull final MessageFrame frame) {
        return (ProxyWorldUpdater) frame.getWorldUpdater();
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
