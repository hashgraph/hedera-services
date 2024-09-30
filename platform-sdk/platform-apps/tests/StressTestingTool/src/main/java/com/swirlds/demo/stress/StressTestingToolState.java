/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.demo.stress;
/*
 * This file is public domain.
 *
 * SWIRLDS MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. SWIRLDS SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.utility.ByteUtils;
import com.swirlds.platform.state.PlatformStateModifier;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.SwirldState;
import com.swirlds.platform.system.events.Event;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.time.Duration;

/**
 * This testing tool simulates configurable processing times for both preHandling and handling for stress testing
 * purposes.
 */
public class StressTestingToolState extends PartialMerkleLeaf implements SwirldState, MerkleLeaf {
    private static final long CLASS_ID = 0x79900efa3127b6eL;

    /** A running sum of transaction contents */
    private long runningSum = 0;

    /** supplies the app config */
    public StressTestingToolConfig config;

    @SuppressWarnings("unused")
    public StressTestingToolState() {}

    private StressTestingToolState(@NonNull final StressTestingToolState sourceState) {
        super(sourceState);
        runningSum = sourceState.runningSum;
        config = sourceState.config;
        setImmutable(false);
        sourceState.setImmutable(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized StressTestingToolState copy() {
        throwIfImmutable();
        return new StressTestingToolState(this);
    }

    /**
     * {@inheritDoc}
     */
    public void init(
            @NonNull final Platform platform,
            @NonNull final InitTrigger trigger,
            @Nullable final SoftwareVersion previousSoftwareVersion) {
        this.config = platform.getContext().getConfiguration().getConfigData(StressTestingToolConfig.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void preHandle(final Event event) {
        busyWait(config.preHandleTime());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handleConsensusRound(final Round round, final PlatformStateModifier platformState) {
        throwIfImmutable();
        round.forEachTransaction(this::handleTransaction);
    }

    private void handleTransaction(@NonNull final ConsensusTransaction trans) {
        if (trans.isSystem()) {
            return;
        }
        runningSum +=
                ByteUtils.byteArrayToLong(trans.getApplicationTransaction().toByteArray(), 0);
        busyWait(config.handleTime());
    }

    @SuppressWarnings("all")
    private void busyWait(@NonNull final Duration duration) {
        if (!duration.isZero() && !duration.isNegative()) {
            final long start = System.nanoTime();
            final long nanos = duration.toNanos();
            while (System.nanoTime() - start < nanos) {
                // busy wait
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(@NonNull final SerializableDataOutputStream out) throws IOException {
        out.writeLong(runningSum);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(@NonNull final SerializableDataInputStream in, final int version) throws IOException {
        runningSum = in.readLong();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return ClassVersion.NO_ADDRESS_BOOK_IN_STATE;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMinimumSupportedVersion() {
        return ClassVersion.NO_ADDRESS_BOOK_IN_STATE;
    }

    /**
     * The version history of this class. Versions that have been released must NEVER be given a different value.
     */
    private static class ClassVersion {
        public static final int NO_ADDRESS_BOOK_IN_STATE = 4;
    }
}
