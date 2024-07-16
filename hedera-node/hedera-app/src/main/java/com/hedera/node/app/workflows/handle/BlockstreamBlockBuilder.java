package com.hedera.node.app.workflows.handle;

import com.hedera.hapi.block.stream.Block;
import com.hedera.hapi.block.stream.BlockHeader;
import com.hedera.hapi.block.stream.BlockProof;
import com.hedera.hapi.block.stream.EventMetadata;
import com.hedera.hapi.block.stream.input.SystemTransaction;

public class BlockstreamBlockBuilder {
    private final BlockHeader.Builder headerBuilder = new BlockHeader.Builder();
    private final EventMetadata.Builder startEventBuilder = new EventMetadata.Builder();
    private final SystemTransaction.Builder systemTransactionBuilder =
            new SystemTransaction.Builder();
    private final TransactionsBuilder txnsBuilder = new TransactionsBuilder();
    private final BlockProof.Builder blockProofBuilder = new BlockProof.Builder();

    public BlockHeader.Builder header() {
        return headerBuilder;
    }

    public EventMetadata.Builder startEvent() {
        return startEventBuilder;
    }

    public SystemTransaction.Builder systemTransaction() {
        return systemTransactionBuilder;
    }

    public TransactionsBuilder transactions() {
        return txnsBuilder;
    }

    public BlockProof.Builder blockProof() {
        return blockProofBuilder;
    }

    public Block build() {
        //todo
        return null;
    }
}
