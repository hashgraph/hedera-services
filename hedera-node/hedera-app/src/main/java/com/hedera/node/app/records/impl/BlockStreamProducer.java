package com.hedera.node.app.records.impl;

import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.node.app.spi.workflows.record.SingleTransaction;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.exceptions.NotImplementedException;

import java.util.stream.Stream;

/**
 * The block streams implementation of {@link StreamProducer}
 *
 * todo: implement this class in another ticket
 */
public class BlockStreamProducer implements StreamProducer {

    @Override
    public void writeStreamItems(Stream<SingleTransaction> txnItems) {
        throw new NotImplementedException();
    }

    @Override
    public Bytes getRunningHash() {
        throw new NotImplementedException();
    }

    @Override
    public Bytes getNMinus3RunningHash() {
        throw new NotImplementedException();
    }

    @Override
    public void initRunningHash(RunningHashes runningHashes) {
        throw new NotImplementedException();
    }

    @Override
    public void close() throws Exception {
        throw new NotImplementedException();
    }
}
