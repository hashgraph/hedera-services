package com.hedera.node.app.records.streams.impl.producers;

import com.hedera.hapi.streams.v7.BlockStateProof;
import com.hedera.node.app.records.impl.BlockRecordManagerImpl;
import com.swirlds.platform.system.transaction.StateSignatureTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * The StateSignatureTransactionCollector is responsible for collecting state signatures from the network and sorting
 * them into queues to later be processed by a BlockStateProducer. If a BlockStateProof arrives for a completed round,
 * any round which is less than or equal to the completed round will be removed from the collector and the
 * BlockStateProof will be added to the queue for that round. This is allowed because a BlockStateProof for a later
 * round proves the validity of rounds before it, and it's possible for a round to never receive enough signatures to
 * prove that round. Thus, we can use a later round's BlockStateProof to prove the validity of earlier rounds.
 */
public class StateSignatureTransactionCollector {

    private static final Logger logger = LogManager.getLogger(BlockRecordManagerImpl.class);

    /**
     * Maintain a list of queues that are used to store state signatures for a given round.
     */
    private final ConcurrentHashMap<Long, LinkedBlockingQueue<QueuedStateSignatureTransaction>> signatureQueues;

    public StateSignatureTransactionCollector() {
        this.signatureQueues = new ConcurrentHashMap<>();
    }

    /**
     * Submit a state signature transaction to the collector. This will be added to the queue for the given round.
     * @param sig the state signature transaction to submit
     */
    public void submitStateSignatureTransaction(@NonNull final StateSignatureTransaction sig) {
        final long roundNum = sig.getRound();
        final var q = signatureQueues.computeIfAbsent(roundNum, k -> new LinkedBlockingQueue<>());
        if (!q.offer(new QueuedStateSignatureTransaction(sig, null))) {
            logger.error("Failed to add state signature to the full unbounded queue for round {}", roundNum);
        }
    }

    /**
     * Get the queue for a given round number. This is used by the BlockStateProducer to consume the signatures for a
     * given round.
     * @param roundNum the round number to get the queue for
     * @return the queue for the given round number
     */
    public LinkedBlockingQueue<QueuedStateSignatureTransaction> getQueueForRound(long roundNum) {
        // If there is no queue for this round, create one.
        return signatureQueues.computeIfAbsent(roundNum, k -> new LinkedBlockingQueue<>());
    }

    /**
     * The caller can signal that it has completed a round, and we can remove the queue for that round and any rounds
     * less than that round.
     * @param proof the completed BlockStateProof for a round
     */
    public void roundComplete(@NonNull BlockStateProof proof) {
        // Remove any buffered signatures for rounds equal to or less than this one. For each round we encounter that is
        // less than the most recently completed round, we should remove them and provide the most recent proof so
        // waiting threads no longer block on them.

        final long roundNum = proof.round();
        signatureQueues.forEach((k, v) -> {
            if (k <= roundNum) {
                v.offer(new QueuedStateSignatureTransaction(new StateSignatureTransaction(), proof));
            }
            signatureQueues.remove(k);
        });
    }
}
