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

package com.hedera.node.app.service.contract.impl.exec.operations.utils;

import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.accessTrackerFor;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.proxyUpdaterFor;

import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.tuweni.units.bigints.UInt256;
import org.hyperledger.besu.evm.frame.MessageFrame;

public class OpUtils {
    private OpUtils() {
        throw new UnsupportedOperationException("Utility Class");
    }

    /**
     * If the given {@code frame} has a {@link com.hedera.node.app.service.contract.impl.infra.StorageAccessTracker}
     * in its context, then tracks the given {@code key} and {@code value} as a read in that tracker.
     *
     * @param frame the frame to (maybe) track the read in
     * @param key the key read
     * @param value the value read
     */
    public static void maybeTrackReadIn(
            @NonNull final MessageFrame frame, @NonNull UInt256 key, @NonNull UInt256 value) {
        final var accessTracker = accessTrackerFor(frame);
        if (accessTracker != null) {
            final var worldUpdater = proxyUpdaterFor(frame);
            final var contractId = worldUpdater.getHederaContractId(frame.getRecipientAddress());
            accessTracker.trackIfFirstRead(contractId.contractNumOrThrow(), key, value);
        }
    }
}
