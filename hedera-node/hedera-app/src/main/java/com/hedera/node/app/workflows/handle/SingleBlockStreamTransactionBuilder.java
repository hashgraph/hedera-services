package com.hedera.node.app.workflows.handle;

import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.block.stream.output.TransactionOutput;
import com.hedera.hapi.block.stream.output.TransactionResult;
import com.hedera.pbj.runtime.io.buffer.Bytes;

public interface SingleBlockStreamTransactionBuilder {
    SingleBlockStreamTransactionBuilder submittedBytes(Bytes submittedBytes);

    TransactionResult.Builder result();

    TransactionOutput.Builder output();

    StateChanges.Builder stateChanges();

    BlockStreamTransaction build();
}
