/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.app.workflows.handle.flow;

import static com.swirlds.platform.system.InitTrigger.EVENT_STREAM_RECOVERY;

import com.hedera.node.app.spi.info.NodeInfo;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.node.app.workflows.handle.flow.annotations.PlatformTransactionScope;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.swirlds.platform.state.PlatformState;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.events.ConsensusEvent;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.state.HederaState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.inject.Inject;

@PlatformTransactionScope
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
    public ProcessRunner(
            @NonNull final SoftwareVersion version,
            @NonNull final InitTrigger initTrigger,
            @NonNull final RecordListBuilder recordListBuilder,
            @NonNull final SkipHandleProcess skipHandleProcess,
            @NonNull final DefaultHandleProcess defaultHandleProcess,
            @NonNull final Instant consensusNow,
            @NonNull final HederaState state,
            @NonNull final PlatformState platformState,
            @NonNull final ConsensusEvent platformEvent,
            @NonNull final NodeInfo creator,
            @NonNull final ConsensusTransaction platformTxn) {
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
        if (this.initTrigger != EVENT_STREAM_RECOVERY && version.compareTo(platformEvent.getSoftwareVersion()) > 0) {
            skipHandleProcess.processUserTransaction(
                    consensusNow, state, platformState, platformEvent, creator, platformTxn, recordListBuilder);
        } else {
            defaultHandleProcess.processUserTransaction(
                    consensusNow, state, platformState, platformEvent, creator, platformTxn, recordListBuilder);
        }
        return recordListBuilder.build().records().stream();
    }
}
