/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.records.streams.impl.producers;

import com.hedera.hapi.block.stream.BlockProof;
import com.hedera.node.app.records.impl.BlockRecordManagerImpl;
import com.swirlds.platform.system.transaction.StateSignatureTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TransferQueue;
import java.util.concurrent.atomic.AtomicLong;

import edu.umd.cs.findbugs.annotations.Nullable;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The StateSignatureTransactionCollector is responsible for collecting state signatures from the network and sorting
 * them into queues to later be processed by a BlockStateProducer. If a BlockStateProof arrives for a completed round,
 * any round which is less than or equal to the completed round will be removed from the collector and the
 * BlockStateProof will be added to the queue for that round. This is allowed because a BlockStateProof for a later
 * round proves the validity of rounds before it, and it's possible for a round to never receive enough signatures to
 * prove that round. Thus, we can use a later round's BlockStateProof to prove the validity of earlier rounds.
 *
 * <p>This class is thread-safe.
 */
public class StateSignatureTransactionCollector {

    private static final Logger logger = LogManager.getLogger(BlockRecordManagerImpl.class);

    /**
     * The maximum number of rounds to keep in the window. This constant defines the maximum allowed difference between
     * the highest and lowest round numbers within the window. The window size helps to constrict the size of the
     * signatureQueues map and prevent it from growing indefinitely.
     */
    private static final int MAX_ROUND_WINDOW_SIZE = 100;

    private final TaskCompletionWindow taskCompletionWindow = new TaskCompletionWindow(MAX_ROUND_WINDOW_SIZE);

    /**
     * Maintain a list of queues that are used to store state signatures for a given round.
     */
    private final ConcurrentHashMap<Long, LinkedTransferQueue<QueuedStateSignatureTransaction>> signatureQueues;

    /**
     * The singleton instance.
     */
    private static final StateSignatureTransactionCollector INSTANCE = new StateSignatureTransactionCollector();

    /*
     * Private constructor to prevent instantiation.
     */
    private StateSignatureTransactionCollector() {
        this.signatureQueues = new ConcurrentHashMap<>();
    }

    /*
     * Public static method to get the instance.
     */
    public static StateSignatureTransactionCollector getInstance() {
        return INSTANCE;
    }

    /**
     * Add a state signature transaction to the collector. This will be added to the queue for the given round.
     * @param nodeId the node id of the node that submitted the state signature transaction
     * @param sig the state signature transaction to submit
     */
    public void putStateSignatureTransaction(long nodeId, @NonNull final StateSignatureTransaction sig) {
        //todo: add API to get round number from StateSignatureTransaction
//        final long roundNum = sig.getRound();
        final long roundNum = 0;
        final var q = getOrCreateQueue(roundNum);
        final var t = new QueuedStateSignatureTransaction(nodeId, sig, null);
        // We can exploit LinkedTransferQueue to attempt a fast-path transfer. If and only if no consumers are blocked
        // polling for the element, fall back to putting it in the queue.
        if (q.tryTransfer(t)) return;
        q.put(t);
    }

    /**
     * Get the queue for a given round number. This is used by the BlockStateProducer to consume the signatures for a
     * given round.
     * @param roundNum the round number to get the queue for
     * @return the queue for the given round number
     */
    @NonNull
    public TransferQueue<QueuedStateSignatureTransaction> getQueueForRound(final long roundNum) {
        // See if there is a queue for this round.
        return getOrCreateQueue(roundNum);
    }

    /**
     * The caller can signal that it has completed a round, and we can remove the queue for that round and any rounds
     * less than that round.
     * @param proof the completed BlockStateProof for a round
     */
    public void roundComplete(@NonNull final BlockProof proof, final long roundNum) {
        // Remove any buffered signatures for rounds equal to or less than this one. For each round we encounter that is
        // less than the most recently completed round, we should remove them and provide the most recent proof so
        // waiting threads no longer block on them.

        // Update the last proven round in our window.
        taskCompletionWindow.completeTask(roundNum);

        // Remove the queue for this round.
        signatureQueues.remove(roundNum);

        // If for some reason we can't finish a round after a certain point, we should clean up these queues. This
        // can happen if we don't get enough signatures to prove a round or if signatures come in later after we have
        // already proven a round. lastProvenRound helps in that we can clean up these rounds that fall outside our
        // window so that this map doesn't grow indefinitely.
        final var lastProvenRound = this.taskCompletionWindow.getLowestCompletedTaskId();
        signatureQueues.forEach((k, v) -> {
            if (k <= lastProvenRound) {
                v.offer(new QueuedStateSignatureTransaction(-1, new StateSignatureTransaction(), proof));
                signatureQueues.remove(k);
            }
        });
    }

    @NonNull
    private LinkedTransferQueue<QueuedStateSignatureTransaction> getOrCreateQueue(final long roundNum) {
        return signatureQueues.computeIfAbsent(roundNum, k -> new LinkedTransferQueue<>());
    }
}
