package com.hedera.node.app.workflows.handle.flow;

import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.state.HederaState;
import edu.umd.cs.findbugs.annotations.NonNull;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.time.Instant;

@Singleton
public class DefaultHandleProcess implements HandleProcess{
    @Inject
    public DefaultHandleProcess() {
    }

    @Override
    public void processUserTransaction(@NonNull final Instant consensusNow, @NonNull final HederaState state, @NonNull final PlatformState platformState, @NonNull final ConsensusEvent platformEvent, @NonNull final NodeInfo creator, @NonNull final ConsensusTransaction platformTxn, @NonNull final RecordListBuilder recordListBuilder) {
        
    }
}
