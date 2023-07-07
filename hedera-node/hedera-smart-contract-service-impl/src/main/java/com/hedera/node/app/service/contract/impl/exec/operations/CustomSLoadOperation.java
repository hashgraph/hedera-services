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

package com.hedera.node.app.service.contract.impl.exec.operations;

import static com.hedera.hapi.streams.SidecarType.CONTRACT_STATE_CHANGE;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.accessTrackerFor;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.proxyUpdaterFor;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.streams.SidecarType;
import com.hedera.node.app.service.contract.impl.exec.FeatureFlags;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.evm.EVM;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.operation.Operation;
import org.hyperledger.besu.evm.operation.SLoadOperation;

/**
 * A wrapper around {@link SLoadOperation} that takes the extra step of tracking the read storage value if
 * {@link FeatureFlags#isSidecarEnabled(MessageFrame, SidecarType)} returns true for the
 * {@link SidecarType#CONTRACT_STATE_CHANGE} type.
 */
public class CustomSLoadOperation implements Operation {
    private final FeatureFlags featureFlags;
    private final SLoadOperation delegate;

    public CustomSLoadOperation(@NonNull final FeatureFlags featureFlags, @NonNull final SLoadOperation delegate) {
        this.featureFlags = requireNonNull(featureFlags);
        this.delegate = requireNonNull(delegate);
    }

    @Override
    public OperationResult execute(@NonNull final MessageFrame frame, @NonNull final EVM evm) {
        requireNonNull(evm);
        requireNonNull(frame);

        final var key = frame.getStackItem(0);
        final var result = delegate.execute(frame, evm);
        if (result.getHaltReason() == null && featureFlags.isSidecarEnabled(frame, CONTRACT_STATE_CHANGE)) {
            // The base SLOAD operation returns its read value on the stack
            final var value = frame.getStackItem(0);
            trackAccessIn(frame, UInt256.fromBytes(key), UInt256.fromBytes(value));
        }
        return result;
    }

    private void trackAccessIn(@NonNull final MessageFrame frame, @NonNull UInt256 key, @NonNull UInt256 value) {
        final var accessTracker = accessTrackerFor(frame);
        if (accessTracker != null) {
            final var worldUpdater = proxyUpdaterFor(frame);
            final var contractId = worldUpdater.getHederaContractId(frame.getRecipientAddress());
            accessTracker.trackIfFirstRead(contractId.contractNumOrThrow(), key, value);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getOpcode() {
        return delegate.getOpcode();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return delegate.getName();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getStackItemsConsumed() {
        return delegate.getStackItemsConsumed();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getStackItemsProduced() {
        return delegate.getStackItemsProduced();
    }
}
