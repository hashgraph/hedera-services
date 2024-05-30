package com.hedera.node.app.workflows.handle.flow;

import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.node.app.workflows.handle.flow.annotations.HandleScope;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.state.HederaState;

import javax.inject.Inject;
import java.time.Instant;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static com.swirlds.platform.system.InitTrigger.EVENT_STREAM_RECOVERY;

@HandleScope
public class ProcessRunner implements Supplier<Stream<SingleTransactionRecord>> {
    private final SoftwareVersion version;
    private final InitTrigger initTrigger;
    private final RecordListBuilder recordListBuilder;
    private final SkipHandleProcess skipHandleProcess;
    private final DefaultHandleProcess defaultHandleProcess;

    final Instant consensusNow;
    final HederaState state;
    final PlatformState platformState;
    final ConsensusEvent platformEvent;
    final NodeInfo creator;
    final ConsensusTransaction platformTxn;

    @Inject
    public ProcessRunner(final SoftwareVersion version,
                         final InitTrigger initTrigger,
                         final RecordListBuilder recordListBuilder,
                         final SkipHandleProcess skipHandleProcess,
                         final DefaultHandleProcess defaultHandleProcess,
                         final Instant consensusNow,
                         final HederaState state,
                         final PlatformState platformState,
                         final ConsensusEvent platformEvent,
                         final NodeInfo creator,
                         final ConsensusTransaction platformTxn) {
        this.version = version;
        this.initTrigger = initTrigger;
        this.recordListBuilder = recordListBuilder;
        this.skipHandleProcess = skipHandleProcess;
        this.defaultHandleProcess = defaultHandleProcess;
        this.consensusNow = consensusNow;
        this.state = state;
        this.platformState = platformState;
        this.platformEvent = platformEvent;
        this.creator = creator;
        this.platformTxn = platformTxn;
    }


    @Override
    public Stream<SingleTransactionRecord> get() {
        if (this.initTrigger != EVENT_STREAM_RECOVERY
                && version.compareTo(platformEvent.getSoftwareVersion()) > 0) {
            skipHandleProcess.processUserTransaction(consensusNow, state, platformState, platformEvent, creator, platformTxn, recordListBuilder);
        } else {
            defaultHandleProcess.processUserTransaction(consensusNow, state, platformState, platformEvent, creator, platformTxn, recordListBuilder);
        }
        return recordListBuilder.build().records().stream();
    }
}
