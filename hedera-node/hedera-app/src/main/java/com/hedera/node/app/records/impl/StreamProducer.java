package com.hedera.node.app.records.impl;

import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.node.app.spi.workflows.record.SingleTransaction;
import com.hedera.pbj.runtime.io.buffer.Bytes;

import java.util.stream.Stream;

/**
 * A {@link StreamProducer} is responsible for writing a stream of {@link SingleTransaction} items to
 * a stream. This stream is the official output of the consensus node, the "blockchain."
 */
public interface StreamProducer extends AutoCloseable {
    /**
     * Takes a stream of {@link SingleTransaction} items to the output stream
     * @param txnItems the transaction items to write
     */
    void writeStreamItems(Stream<SingleTransaction> txnItems);

    //??? Not clear if these running hash methods are needed/will be used with block streams...

    /**
     * Returns the running hash of all transactions, or in other words, the ongoing hash that is updated
     * with each processed transaction. This method should block if the running hash has not yet
     * been computed for the most recent user transaction.
     *
     * @return the running hash
     */
    Bytes getRunningHash();

    /**
     * Returns the running hash of all transactions, from three transactions in the past.
     * @return the n-minus-3 running hash
     */
    Bytes getNMinus3RunningHash();

    /**
     * Initializes the running hash with the given {@link RunningHashes} object.
     * @param runningHashes the latest running hashes of the chain
     */
    void initRunningHash(RunningHashes runningHashes);
}
