package com.hedera.node.app.workflows.handle.flow;

import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.state.HederaState;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.time.Instant;

public interface HandleProcess {

    void processUserTransaction(@NonNull final Instant consensusNow,
                                              @NonNull final HederaState state,
                                              @NonNull final PlatformState platformState,
                                              @NonNull final ConsensusEvent platformEvent,
                                              @NonNull final NodeInfo creator,
                                              @NonNull final ConsensusTransaction platformTxn,
                                              @NonNull final RecordListBuilder recordListBuilder);
}
