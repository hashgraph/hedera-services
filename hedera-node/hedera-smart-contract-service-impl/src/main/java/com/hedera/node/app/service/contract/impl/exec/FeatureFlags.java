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

package com.hedera.node.app.service.contract.impl.exec;

import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.configOf;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.contractsConfigOf;

import com.hedera.hapi.streams.SidecarType;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import org.hyperledger.besu.evm.frame.MessageFrame;

/**
 * Provides feature flags used to customize behavior of Hedera {@link org.hyperledger.besu.evm.operation.Operation} overrides.
 */
public interface FeatureFlags {
    /**
     * Whether the {@code CREATE2} operation should be enabled for the given {@code frame}.
     *
     * @param frame the {@link MessageFrame} to check
     * @return whether {@code CREATE2} should be enabled
     */
    boolean isCreate2Enabled(@NonNull MessageFrame frame);

    /**
     * Whether the sidecar of a given type is enabled.
     *
     * @param frame the {@link MessageFrame} to check
     * @return whether the given sidecar type is enabled
     */
    default boolean isSidecarEnabled(@NonNull MessageFrame frame, @NonNull SidecarType sidecarType) {
        return contractsConfigOf(frame).sidecars().contains(sidecarType);
    }

    /**
     * Whether "implicit creation" of accounts via sending value or targeting a {@code CREATE2} to an EIP-1014 address
     * should be enabled for the given {@code frame}.
     *
     * @param frame the {@link MessageFrame} to check
     * @return whether implicit creation should be enabled
     */
    default boolean isImplicitCreationEnabled(@NonNull MessageFrame frame) {
        return isImplicitCreationEnabled(configOf(frame));
    }

    /**
     * Whether "implicit creation" of accounts via sending value or targeting a {@code CREATE2} to an EIP-1014 address
     * should be enabled for the given {@code frame}.
     *
     * @param config the {@link Configuration} to check
     * @return whether implicit creation should be enabled
     */
    boolean isImplicitCreationEnabled(@NonNull Configuration config);

    /**
     * If true calls to non-existing contract addresses will result in a successful NOOP.  If false,
     * calls such calls will result in a revert with status {@code INVALID_SOLIDITY_ADDRESS}.
     * @param config the {@link Configuration}
     * @param possiblyGrandFatheredEntityNum the account number to check for grandfathering
     * @return true if calls to non-existing contract addresses will result in a successful NOOP.
     */
    default boolean isAllowCallsToNonContractAccountsEnabled(
            @NonNull Configuration config, @Nullable Long possiblyGrandFatheredEntityNum) {
        return false;
    }
    ;

    /**
     *  If true, charge intrinsic gas for calls that fail with a pre-EVM exception.
     * @return true whether to  charge intrinsic gas for calls that fail with a pre-EVM exception.
     */
    default boolean isChargeGasOnPreEvmException(@NonNull Configuration config) {
        return false;
    }
}
