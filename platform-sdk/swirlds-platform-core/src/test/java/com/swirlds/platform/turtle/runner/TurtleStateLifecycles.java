// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.turtle.runner;

import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.utility.NonCryptographicHashing;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.state.StateLifecycles;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.Event;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.function.Consumer;

/**
 * This class handles the lifecycle events for the {@link TurtleTestingToolState}.
 */
enum TurtleStateLifecycles implements StateLifecycles<TurtleTestingToolState> {
    TURTLE_STATE_LIFECYCLES;

    @Override
    public void onPreHandle(
            @NonNull Event event,
            @NonNull TurtleTestingToolState state,
            @NonNull Consumer<ScopedSystemTransaction<StateSignatureTransaction>> stateSignatureTransactionCallback) {
        // no op
    }

    @Override
    public void onHandleConsensusRound(
            @NonNull Round round,
            @NonNull TurtleTestingToolState turtleTestingToolState,
            @NonNull Consumer<ScopedSystemTransaction<StateSignatureTransaction>> stateSignatureTransactionCallback) {
        turtleTestingToolState.state = NonCryptographicHashing.hash64(
                turtleTestingToolState.state,
                round.getRoundNum(),
                round.getConsensusTimestamp().getNano(),
                round.getConsensusTimestamp().getEpochSecond());
    }

    @Override
    public boolean onSealConsensusRound(@NonNull Round round, @NonNull TurtleTestingToolState state) {
        // no op
        return true;
    }

    @Override
    public void onStateInitialized(
            @NonNull TurtleTestingToolState state,
            @NonNull Platform platform,
            @NonNull InitTrigger trigger,
            @Nullable SoftwareVersion previousVersion) {
        // no op
    }

    @Override
    public void onUpdateWeight(
            @NonNull TurtleTestingToolState state,
            @NonNull AddressBook configAddressBook,
            @NonNull PlatformContext context) {
        // no op
    }

    @Override
    public void onNewRecoveredState(@NonNull TurtleTestingToolState recoveredState) {
        // no op
    }
}
