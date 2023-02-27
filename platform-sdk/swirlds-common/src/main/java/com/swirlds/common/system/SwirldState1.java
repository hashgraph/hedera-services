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

import com.swirlds.common.Releasable;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.system.transaction.Transaction;
import java.time.Instant;
import java.util.List;

/**
 * A {@link SwirldState} that may be updated by both pre-consensus and consensus transactions and remains mutable
 * after being copied.
 * <p>
 * If an app's state class implements SwirldState1, then the platform will instantiate it to create a
 * state object X, and will transactions to it by calling X.handleTransaction() and X.handleConsensusRound(). Each
 * transaction is sent to X only once.
 * <p>
 * First the Platform will give X one or more rounds of consensus ordered transactions by calling X
 * .handleConsensusRound(). Then it will make a fast copy of X, creating a new object Y. Then it will send X multiple
 * transactions that have not yet reached consensus by calling X.handleTransaction(). Eventually, it will stop sending
 * transactions to X, and switch to sending transactions to Y. It will start by sending multiple transactions that
 * were pre-consensus when they were sent to X, but now have reached consensus when they are sent to Y. In this way,
 * any particular state object will see each transaction only once, and the platform frequently makes fast copies of
 * the objects. The computer may handle each transaction multiple times, but any particular object only handles it
 * once. This is useful, because it lets the app respond to transactions even before they have a consensus order, but
 * then can re-respond appropriately when that order changes. The app doesn't have to be written specially to deal
 * with this changing history. The Platform takes care of managing all the version control as the order of history
 * keeps changing.
 */
public non-sealed interface SwirldState1 extends SwirldState {

    /**
     * Provides the application an opportunity to perform operations on transactions prior to handling. Called against a
     * given {@link Transaction} only once, globally (not once per state instance). This method may modify the given
     * {@link Transaction} by doing nothing, adding additional signatures, removing existing signatures,
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
     * <strong>Modifications must not be made to the state in this method.</strong>
     *
     * @param transaction
     * 		the transaction to perform pre-handling on
     * @see #handleTransaction(long, Instant, Instant, Transaction, SwirldDualState)
     * @see #handleConsensusRound(Round, SwirldDualState)
     */
    default void preHandle(final Transaction transaction) {}

    /**
     * Given a transaction that has not yet reached consensus, update the state to reflect its effect.
     *
     * <p>
     * The state of this object must NEVER change except inside the methods below.
     *
     * <ul>
     *     <li>{@link #init(Platform, SwirldDualState, InitTrigger, SoftwareVersion)}</li>
     *     <li>{@link #copy()}</li>
     *     <li>handle methods</li>
     *     <li><ul>
     *     <li>{@link #handleConsensusRound(Round, SwirldDualState)}</li>
     *     <li>{@link #handleTransaction(long, Instant, Instant, Transaction, SwirldDualState)}</li>
     *     </ul></li>
     *  </ul>
     *
     * <p>
     * So it is good if the handle methods change some class variables and then return. It is also OK if the
     * handle methods spawn a number of threads that change those variables, then wait until all those threads have
     * ended, and then return. It is even OK for a handle method to create a pool of threads that continue to exist
     * after it returns, as long as it ensures that those threads have finished all their changes before
     * it returns. But it is an error for a handle method to spawn a thread that will make changes after
     * the handle method returns and before the next time a handle method is called. If any of the handle methods
     * create threads that continue to exist after it returns (in a legal way), then it should clean up those resources
     * when the object has been fully released (see documentation in {@link Releasable}). If the SwirldState extends
     * one of the partial merkle implementations (e.g. PartialMerkleLeaf or PartialNaryMerkleInternal) then it can put
     * that cleanup in the onDestroy() method, which is called when the object becomes fully released.
     *
     * @param creatorId
     * 		the ID number of the member who created this transaction
     * @param timeCreated
     * 		the time when this transaction was first created and sent to the network, as claimed by
     * 		the member that created it (which might be dishonest or mistaken)
     * @param estimatedTimestamp
     * 		an estimate of what the consensus timestamp of the {@code trans} will be
     * @param trans
     * 		the transaction to handle, encoded any way the swirld app author chooses
     * @param swirldDualState
     * 		current dualState object which can be read/written by the application
     * @see #handleConsensusRound(Round, SwirldDualState)
     */
    void handleTransaction(
            final long creatorId,
            final Instant timeCreated,
            final Instant estimatedTimestamp,
            final Transaction trans,
            final SwirldDualState swirldDualState);

    /**
     * {@inheritDoc}
     * <p>
     * A given SwirldState object will see a sequence of rounds, followed by a sequence of transactions that
     * have not yet reached consensus via {@link #handleTransaction(long, Instant, Instant, Transaction,
     * SwirldDualState)}. Pre-consensus transactions are sent in an order that is the current best guess as to what the
     * consensus will be. But that order is subject to change. A given SwirldState object will never see that order
     * change. But a different SwirldState object may be instantiated by the {@link Platform}, and it may receive those
     * events in a different order.
     * <p>
     * The state of this object must NEVER change except inside the methods below.
     *
     * <ul>
     *     <li>{@link #init(Platform, SwirldDualState, InitTrigger, SoftwareVersion)}</li>
     *     <li>{@link #copy()}</li>
     *     <li>handle methods</li>
     *     <li><ul>
     *     <li>{@link #handleConsensusRound(Round, SwirldDualState)}</li>
     *     <li>{@link #handleTransaction(long, Instant, Instant, Transaction, SwirldDualState)}</li>
     *     </ul></li>
     *  </ul>
     * <p>
     * If signature verification was started on a background thread in {@link #preHandle(Transaction)}, the process
     * should be checked for completion. Accessing {@link TransactionSignature#getSignatureStatus()} before this
     * process is complete will cause it to return {@code
     * null}:
     *
     * <pre>
     *     for (TransactionSignature sig : transaction.getSignatures()) {
     *         Future&lt;Void&gt; future = sig.waitForFuture().get();
     *     }
     * </pre>
     */
    @Override
    void handleConsensusRound(final Round round, final SwirldDualState swirldDualState);
}
