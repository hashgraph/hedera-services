package com.hedera.node.app.workflows.handle;

import com.hedera.hapi.block.stream.output.StateChanges;
import com.hedera.hapi.block.stream.output.TransactionOutput;
import com.hedera.hapi.block.stream.output.TransactionResult;
import com.hedera.pbj.runtime.io.buffer.Bytes;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

public record BlockStreamTransaction(@NonNull Bytes submittedBytes,
                                     @NonNull TransactionResult result, @Nullable
                                     TransactionOutput output, @NonNull StateChanges stateChanges) {
}
