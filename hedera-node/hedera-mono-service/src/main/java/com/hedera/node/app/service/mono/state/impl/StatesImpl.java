/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.state.impl;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.node.app.service.mono.ServicesState;
import com.hedera.node.app.service.mono.context.MutableStateChildren;
import com.hedera.node.app.spi.state.State;
import com.hedera.node.app.spi.state.States;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import org.apache.commons.lang3.NotImplementedException;

public class StatesImpl implements States {
    private final MutableStateChildren children = new MutableStateChildren();

    public StatesImpl() {
        /* Default constructor */
    }

    /**
     * Updates children (e.g., MerkleMaps and VirtualMaps) from given immutable state. This should
     * be called before making an attempt to expand the platform signatures linked to the given
     * transaction.
     */
    public void updateChildren(final ServicesState sourceState) {
        children.updateFromImmutable(sourceState, sourceState.getTimeOfLastHandledTxn());
    }

    @Override
    public @NonNull <K, V> State<K, V> get(@NonNull final String stateKey) {
        Objects.requireNonNull(stateKey);

        if (stateKey.equals("ACCOUNTS")) {
            final var accounts = children.accounts();
            return (State<K, V>)
                    (accounts.areOnDisk()
                            ? new OnDiskStateImpl<>(
                                    stateKey, accounts.getOnDiskAccounts(), children.signedAt())
                            : new InMemoryStateImpl<>(
                                    stateKey, accounts.getInMemoryAccounts(), children.signedAt()));
        } else if (stateKey.equals("ALIASES")) {
            final var state =
                    new RebuiltStateImpl<>(stateKey, children.aliases(), children.signedAt());
            return (StateBase) state;
        } else if (stateKey.equals("SCHEDULES-BY-EQUALITY")) {
            final var state =
                    new InMemoryStateImpl<>(
                            stateKey, children.schedules().byEquality(), children.signedAt());
            return (StateBase) state;
        } else if (stateKey.equals("SCHEDULES-BY-ID")) {
            final var state =
                    new InMemoryStateImpl<>(
                            stateKey, children.schedules().byId(), children.signedAt());
            return (StateBase) state;
        } else if (stateKey.equals("SCHEDULES-BY-EXPIRY")) {
            final var state =
                    new InMemoryStateImpl<>(
                            stateKey,
                            children.schedules().byExpirationSecond(),
                            children.signedAt());
            return (StateBase) state;
        } else {
            throw new NotImplementedException(
                    String.format("State key %s not supported", stateKey));
        }
    }

    @VisibleForTesting
    public MutableStateChildren getChildren() {
        return children;
    }
}
