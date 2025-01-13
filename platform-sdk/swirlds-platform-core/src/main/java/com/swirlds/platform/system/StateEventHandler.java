/*
 * Copyright (C) 2016-2025 Hedera Hashgraph, LLC
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

package com.swirlds.platform.system;

import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.swirlds.base.state.Mutable;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.Event;
import com.swirlds.platform.system.transaction.Transaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A Swirld app is defined by creating two classes, one implementing {@link SwirldMain}, and the other
 * {@link StateEventHandler}. The class that implements the StateEventHandler should have a zero-argument constructor.
 */
public interface StateEventHandler extends Mutable {

    /**
     * /**
     * Provides the application an opportunity to perform operations on transactions in an event prior to handling.
     * Called against a given {@link Event} only once, globally (not once per state instance) This method may modify the
     * {@link Transaction}s in the event by doing nothing, adding additional signatures, removing existing signatures,
     * replacing signatures with versions that expand the public key from an application specific identifier to an
     * actual public key, or attaching metadata. Additional signatures extracted from the transaction payload can also
     * be added to the list of signatures to be verified.
     * <p>
     * <strong>This method is always invoked on an immutable state.</strong>
     *
     * @param event the event to perform pre-handling on
     * @param stateSignatureTransaction a consumer that accepts a list of {@link ScopedSystemTransaction}s that
     *                                   will be used for callbacks
     */
    default void preHandle(
            final Event event,
            final Consumer<ScopedSystemTransaction<StateSignatureTransaction>> stateSignatureTransaction) {}

    /**
     * This method should apply the transactions in the provided round to the state. Only called on mutable states.
     *
     * @param round         the round to apply
     * @param stateSignatureTransaction a consumer that accepts a list of {@link ScopedSystemTransaction}s that
     *                                   will be used for callbacks
     */
    void handleConsensusRound(
            final Round round,
            final Consumer<ScopedSystemTransaction<StateSignatureTransaction>> stateSignatureTransaction);

    /**
     * Called by the platform after it has made all its changes to this state for the given round.
     * @param round the round whose platform state changes are completed
     */
    default void sealConsensusRound(@NonNull final Round round) {
        // No-op, only implemented by applications that externalize state changes
    }

    /**
     * Implementations of the StateEventHandler should always override this method in production.  The AddressBook returned
     * should have the same Address entries as the configuration AddressBook, but with the weight values updated.
     * <p>
     * The default implementation of this method is provided for use in testing and to prevent compilation failure of
     * implementing classes that have not yet implemented this method.
     *
     * @param configAddressBook the address book as loaded from config.txt. This address book may contain new nodes not
     *                          present in the stateAddressBook. Must not be null.
     * @param context           the platform context. Must not be null.
     * @return a copy of the configuration address book with updated weight.
     */
    @NonNull
    default AddressBook updateWeight(
            @NonNull final AddressBook configAddressBook, @NonNull final PlatformContext context) {
        Objects.requireNonNull(configAddressBook, "configAddressBook must not be null");
        Objects.requireNonNull(context, "context must not be null");
        return configAddressBook;
    }
}
