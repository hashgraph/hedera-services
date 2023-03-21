/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.system;

import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.events.Event;
import com.swirlds.common.system.transaction.Transaction;
import java.util.List;

/**
 * A Swirld app is defined by creating two classes, one implementing {@link SwirldMain}, and the other
 * {@link SwirldState}. The class that implements the SwirldState should have a zero-argument constructor.
 */
public interface SwirldState extends MerkleNode {

    /**
     * <p>
     * Initialize a state. Called exactly once each time a node creates/recreates a state (e.g. restart, reconnect,
     * genesis).
     * </p>
     *
     * <p>
     * If applicable, the application should check to see if the address book has changed, and if so those changes
     * should be handled (in the future the address book will not be changed in this method). It may also be convenient
     * for the application to initialize internal data structures at this time.
     * </p>
     *
     * @param platform                the Platform that instantiated this state
     * @param swirldDualState         the dual state instance used by the initialization function
     * @param trigger                 describes the reason why the state was created/recreated
     * @param previousSoftwareVersion the previous version of the software, {@link SoftwareVersion#NO_VERSION} if this
     *                                is genesis or if migrating from code from before the concept of an application
     *                                software version
     */
    default void init(
            final Platform platform,
            final SwirldDualState swirldDualState,
            final InitTrigger trigger,
            final SoftwareVersion previousSoftwareVersion) {
        // Override if needed
    }

    /**
     * Provides the application an opportunity to perform operations on transactions in an event prior to handling.
     * Called against a given {@link Event} only once, globally (not once per state instance) This method may modify the
     * {@link Transaction}s in the event by doing nothing, adding additional signatures, removing existing signatures,
     * replacing signatures with versions that expand the public key from an application specific identifier to an
     * actual public key, or attaching metadata. Additional signatures extracted from the transaction payload can also
     * be added to the list of signatures to be verified.
     * <p>
     * If signature verification is desired, it is recommended that process be started in this method on a background
     * thread using one of the methods below to give it time to complete before the transaction is handled
     * post-consensus.
     * <ul>
     *     <li>{@link com.swirlds.common.crypto.Cryptography#verifyAsync(TransactionSignature)}</li>
     *     <li>{@link com.swirlds.common.crypto.Cryptography#verifyAsync(List)}</li>
     * </ul>
     * <p>
     * <strong>This method is always invoked on an immutable state.</strong>
     *
     * @param event the event to perform pre-handling on
     * @see #handleConsensusRound(Round, SwirldDualState)
     */
    default void preHandle(final Event event) {}

    /**
     * {@inheritDoc}
     * <p>
     * The state of this object must NEVER change except inside the methods below.
     *
     * <ul>
     *     <li>{@link #init(Platform, SwirldDualState, InitTrigger, SoftwareVersion)}</li>
     *     <li>{@link #copy()}</li>
     *     <li>{@link #handleConsensusRound(Round, SwirldDualState)}</li>
     *  </ul>
     * <p>
     * If signature verification was started on a background thread in {@link #preHandle(Event)}, the process
     * should be checked for completion. Accessing {@link TransactionSignature#getSignatureStatus()} before this
     * process is complete will cause it to return {@code null}:
     *
     * <pre>
     *     for (TransactionSignature sig : transaction.getSignatures()) {
     *         Future&lt;Void&gt; future = sig.waitForFuture().get();
     *     }
     * </pre>
     */
    void handleConsensusRound(final Round round, final SwirldDualState swirldDualState);

    /**
     * Implementations of the SwirldState should always override this method in production.  The AddressBook returned
     * should have the same Adddress entries as the configuration AddressBook, but with the stake values updated. The
     * AddressBook previously saved in the state, if it exists, is provided for reference.
     * <p>
     * The default implementation of this method is provided for use in testing and to prevent compilation failure of
     * implementing classes that have not yet implemented this method.
     *
     * @param configAddressBook the address book as loaded from config.txt. This address book may contain new nodes not
     *                          present in the stateAddressBook. Must not be null.
     * @return a copy of the configuration address book with updated stake.
     */
    default AddressBook updateStake(final AddressBook configAddressBook) {
        return configAddressBook;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    SwirldState copy();
}
