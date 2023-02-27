/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
import com.swirlds.common.system.events.Event;
import com.swirlds.common.system.transaction.Transaction;
import java.util.List;

/**
 * A {@link SwirldState} that is only updates by consensus transactions and becomes immutable once copied.
 * <p>
 * When using {@link SwirldState2}, the Platform does not have to make frequent fast copies of the state like it does
 * for {@link SwirldState1} (though it will still make some fast copies, for other reasons). An app implementing
 * {@link SwirldState2} does not have the ability to affect its state or the shared state with pre-consensus
 * transactions. If there is a need to actually show their effect on the state, then it must implement {@link
 * SwirldState1}. But if it is acceptable to delay the effect until consensus, then {@link SwirldState2} may be more
 * efficient.
 */
public non-sealed interface SwirldState2 extends SwirldState {

    /**
     * Provides the application an opportunity to perform operations on transactions in an event prior to handling.
     * Called against a given {@link Event} only once, globally (not once per state instance) This method may modify
     * the {@link Transaction}s in the event by doing nothing, adding additional signatures, removing existing
     * signatures, replacing signatures with versions that expand the public key from an application specific
     * identifier to an actual public key, or attaching metadata. Additional signatures extracted from the transaction
     * payload can also be added to the list of signatures to be verified.
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
     * @param event
     * 		the event to perform pre-handling on
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
}
