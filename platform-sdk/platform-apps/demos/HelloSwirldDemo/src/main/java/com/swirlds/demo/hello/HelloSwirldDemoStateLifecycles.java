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

package com.swirlds.demo.hello;

import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.state.StateLifecycles;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.Event;
import com.swirlds.platform.system.transaction.Transaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/**
 * This class handles the lifecycle events for the {@link HelloSwirldDemoState}.
 */
public class HelloSwirldDemoStateLifecycles implements StateLifecycles<HelloSwirldDemoState> {

    @Override
    public boolean onHandleConsensusRound(
            @NonNull Round round,
            @NonNull HelloSwirldDemoState state,
            @NonNull Consumer<ScopedSystemTransaction<StateSignatureTransaction>> stateSignatureTransactionCallback) {
        state.throwIfImmutable();
        round.forEachTransaction(v -> handleTransaction(v, state));
        return true;
    }

    private void handleTransaction(final Transaction transaction, HelloSwirldDemoState state) {
        if (transaction.isSystem()) {
            return;
        }
        state.getStrings()
                .add(new String(transaction.getApplicationTransaction().toByteArray(), StandardCharsets.UTF_8));
    }

    @Override
    public void onPreHandle(
            @NonNull Event event,
            @NonNull HelloSwirldDemoState state,
            @NonNull Consumer<ScopedSystemTransaction<StateSignatureTransaction>> stateSignatureTransactionCallback) {
        // no-op
    }

    @Override
    public void onSealConsensusRound(@NonNull Round round, @NonNull HelloSwirldDemoState state) {
        // no-op
    }

    @Override
    public void onStateInitialized(
            @NonNull HelloSwirldDemoState state,
            @NonNull Platform platform,
            @NonNull InitTrigger trigger,
            @Nullable SoftwareVersion previousVersion) {
        // no-op
    }

    @Override
    public void onUpdateWeight(
            @NonNull HelloSwirldDemoState state,
            @NonNull AddressBook configAddressBook,
            @NonNull PlatformContext context) {
        // no-op
    }

    @Override
    public void onNewRecoveredState(@NonNull HelloSwirldDemoState recoveredState) {
        // no-op// no-op
    }
}
