package com.hedera.node.app.records.streams.impl.producers;

import com.hedera.node.app.records.impl.BlockRecordManagerImpl;
import com.swirlds.platform.system.transaction.StateSignatureTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.TreeMap;

/**
 * The StateSignatureTransactionCollector is responsible for collecting state signatures from the network and sorting
 * them into queues to later be processed by a BlockStateProducer.
 */
public class StateSignatureTransactionCollector {

    private static final Logger logger = LogManager.getLogger(BlockRecordManagerImpl.class);

    /**
     * Maintain a list of queues that are used to store state signatures for a given round.
     */
    private final ConcurrentHashMap<Long, LinkedBlockingQueue<QueuedStateSignatureTransaction>> signatureQueues =
            new ConcurrentHashMap<>();

    public StateSignatureTransactionCollector() {}

    public void submitStateSignatureTransaction(@NonNull final StateSignatureTransaction sig) {
        final long roundNum = sig.getRound();
        final var q = signatureQueues.computeIfAbsent(roundNum, k -> new LinkedBlockingQueue<>());
        if (!q.offer(new QueuedStateSignatureTransaction(sig, false))) {
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
     * The caller can signal that it has completed a round and we can remove the queue for that round and any rounds
     * less than that round.
     * @param roundNum the round number that was completed
     */
    public void roundComplete(long roundNum) {
        signatureQueues.remove(roundNum);
        // Remove any buffered signatures for rounds less than this one. We need to put a poison pill in any that we
        // remove so waiting threads no longer block on them.
        signatureQueues.forEach((k, v) -> {
            if (k < roundNum) {
                v.offer(new QueuedStateSignatureTransaction(new StateSignatureTransaction(), true));
            }
            signatureQueues.remove(k);
        });
    }
}
