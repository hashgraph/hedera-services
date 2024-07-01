package com.hedera.node.app.workflows.handle.stack;

import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import com.hedera.node.app.state.WrappedHederaState;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.List;

/**
 * A save point that contains the current state and the record builders created in the current savepoint.
 */
public record SavePoint(@NonNull WrappedHederaState state, @NonNull List<SingleTransactionRecordBuilder> recordBuilders) {}
