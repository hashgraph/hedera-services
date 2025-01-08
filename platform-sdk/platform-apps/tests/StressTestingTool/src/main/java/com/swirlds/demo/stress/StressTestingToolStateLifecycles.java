/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.utility.ByteUtils;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.state.StateLifecycles;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.Event;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Duration;
import java.util.function.Consumer;

public class StressTestingToolStateLifecycles implements StateLifecycles<StressTestingToolState> {

    /** supplies the app config */
    private StressTestingToolConfig config;

    @Override
    public void onStateInitialized(
            @NonNull StressTestingToolState state,
            @NonNull Platform platform,
            @NonNull InitTrigger trigger,
            @Nullable SoftwareVersion previousVersion) {
        this.config = platform.getContext().getConfiguration().getConfigData(StressTestingToolConfig.class);
    }

    @Override
    public void onPreHandle(
            @NonNull Event event,
            @NonNull StressTestingToolState state,
            @NonNull Consumer<ScopedSystemTransaction<StateSignatureTransaction>> stateSignatureTransactionCallback) {
        busyWait(config.preHandleTime());
    }

    @SuppressWarnings("StatementWithEmptyBody")
    private void busyWait(@NonNull final Duration duration) {
        if (!duration.isZero() && !duration.isNegative()) {
            final long start = System.nanoTime();
            final long nanos = duration.toNanos();
            while (System.nanoTime() - start < nanos) {
                // busy wait
            }
        }
    }

    @Override
    public void onHandleConsensusRound(
            @NonNull Round round,
            @NonNull StressTestingToolState state,
            @NonNull Consumer<ScopedSystemTransaction<StateSignatureTransaction>> stateSignatureTransactionCallback) {
        state.throwIfImmutable();
        round.forEachTransaction(v -> handleTransaction(v, state));
    }

    private void handleTransaction(@NonNull final ConsensusTransaction trans, StressTestingToolState state) {
        if (trans.isSystem()) {
            return;
        }

        state.incrementRunningSum(
                ByteUtils.byteArrayToLong(trans.getApplicationTransaction().toByteArray(), 0));
        busyWait(config.handleTime());
    }

    @Override
    public void onSealConsensusRound(@NonNull Round round, @NonNull StressTestingToolState state) {
        // no-op
    }

    @Override
    public void onUpdateWeight(
            @NonNull StressTestingToolState state,
            @NonNull AddressBook configAddressBook,
            @NonNull PlatformContext context) {
        // no-op
    }

    @Override
    public void onNewRecoveredState(@NonNull StressTestingToolState recoveredState) {
        // no-op
    }
}
