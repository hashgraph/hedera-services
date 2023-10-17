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

import static com.hedera.hapi.streams.SidecarType.CONTRACT_ACTION;
import static com.hedera.node.app.service.evm.store.contracts.HederaEvmWorldStateTokenAccount.TOKEN_PROXY_ACCOUNT_NONCE;
import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.contract.impl.exec.gas.TinybarValues;
import com.hedera.node.app.service.contract.impl.infra.StorageAccessTracker;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import com.hedera.node.config.data.ContractsConfig;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class FrameUtils {
    public static final String CONFIG_CONTEXT_VARIABLE = "contractsConfig";
    public static final String TRACKER_CONTEXT_VARIABLE = "storageAccessTracker";
    public static final String TINYBAR_VALUES_VARIABLE = "tinybarValues";

    private FrameUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    public static @NonNull Configuration configOf(@NonNull final MessageFrame frame) {
        return requireNonNull(initialFrameOf(frame).getContextVariable(CONFIG_CONTEXT_VARIABLE));
    }

    public static @NonNull ContractsConfig contractsConfigOf(@NonNull final MessageFrame frame) {
        return configOf(frame).getConfigData(ContractsConfig.class);
    }

    public static boolean hasActionSidecarsEnabled(@NonNull final MessageFrame frame) {
        return contractsConfigOf(frame).sidecars().contains(CONTRACT_ACTION);
    }

    public static boolean hasActionValidationEnabled(@NonNull final MessageFrame frame) {
        return contractsConfigOf(frame).sidecarValidationEnabled();
    }

    public static boolean hasValidatedActionSidecarsEnabled(@NonNull final MessageFrame frame) {
        final var contractsConfig = contractsConfigOf(frame);
        return contractsConfig.sidecars().contains(CONTRACT_ACTION) && contractsConfig.sidecarValidationEnabled();
    }

    public static @Nullable StorageAccessTracker accessTrackerFor(@NonNull final MessageFrame frame) {
        return initialFrameOf(frame).getContextVariable(TRACKER_CONTEXT_VARIABLE);
    }

    public static @NonNull ProxyWorldUpdater proxyUpdaterFor(@NonNull final MessageFrame frame) {
        return (ProxyWorldUpdater) frame.getWorldUpdater();
    }

    public static @NonNull TinybarValues tinybarValuesFor(@NonNull final MessageFrame frame) {
        return initialFrameOf(frame).getContextVariable(TINYBAR_VALUES_VARIABLE);
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

    public static boolean unqualifiedDelegateDetected(final MessageFrame frame) {
        if (!isDelegateCall(frame)) {
            return false;
        }

        final var recipient = frame.getRecipientAddress();
        // but we accept delegates if the token redirect contract calls us,
        // so if they are not a token, then we are a delegate and we are done.
        if (isToken(frame, recipient)) {
            // make sure we have a parent calling context
            final var stack = frame.getMessageFrameStack();
            final var frames = stack.iterator();
            frames.next();
            if (!frames.hasNext()) {
                // Impossible to get here w/o a catastrophic EVM bug
                return false;
            }
            // If the token redirect contract was called via delegate, then it's a delegate
            return isDelegateCall(frames.next());
        }
        return true;
    }

    private static boolean isToken(final MessageFrame frame, final Address address) {
        final var account = frame.getWorldUpdater().get(address);
        if (account != null) {
            return account.getNonce() == TOKEN_PROXY_ACCOUNT_NONCE;
        }
        return false;
    }

    private static @NonNull MessageFrame initialFrameOf(@NonNull final MessageFrame frame) {
        final var stack = frame.getMessageFrameStack();
        return stack.isEmpty() ? frame : stack.getLast();
    }
}
