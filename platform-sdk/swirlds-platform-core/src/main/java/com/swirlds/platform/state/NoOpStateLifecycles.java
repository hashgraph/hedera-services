// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.state;

import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
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
 * A no-op implementation of {@link StateLifecycles} that does nothing.
 * It's useful for auxiliary code that doesn't handle new transactions (State Editor, State commands, Event Recovery workflow, etc.).
 */
public enum NoOpStateLifecycles implements StateLifecycles<PlatformMerkleStateRoot> {
    NO_OP_STATE_LIFECYCLES;

    @Override
    public void onPreHandle(
            @NonNull Event event,
            @NonNull PlatformMerkleStateRoot state,
            @NonNull Consumer<ScopedSystemTransaction<StateSignatureTransaction>> stateSignatureTransactionCallback) {
        // no-op
    }

    @Override
    public void onHandleConsensusRound(
            @NonNull Round round,
            @NonNull PlatformMerkleStateRoot state,
            @NonNull Consumer<ScopedSystemTransaction<StateSignatureTransaction>> stateSignatureTransactionCallback) {
        // no-op
    }

    @Override
    public boolean onSealConsensusRound(@NonNull Round round, @NonNull PlatformMerkleStateRoot state) {
        // no-op
        return true;
    }

    @Override
    public void onStateInitialized(
            @NonNull PlatformMerkleStateRoot state,
            @NonNull Platform platform,
            @NonNull InitTrigger trigger,
            @Nullable SoftwareVersion previousVersion) {
        // no-op
    }

    @Override
    public void onUpdateWeight(
            @NonNull PlatformMerkleStateRoot state,
            @NonNull AddressBook configAddressBook,
            @NonNull PlatformContext context) {
        // no-op
    }

    @Override
    public void onNewRecoveredState(@NonNull PlatformMerkleStateRoot recoveredState) {
        // no-op
    }
}
