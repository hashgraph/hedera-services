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

package com.hedera.node.app.workflows.handle.flow.process;

import static com.swirlds.platform.system.InitTrigger.EVENT_STREAM_RECOVERY;

import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.state.SingleTransactionRecord;
import com.hedera.node.app.workflows.handle.flow.annotations.UserTransactionScope;
import com.hedera.node.app.workflows.handle.flow.modules.UserTransactionComponent;
import com.hedera.node.app.workflows.handle.record.RecordListBuilder;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.SoftwareVersion;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.inject.Inject;

@UserTransactionScope
public class ProcessRunner implements Supplier<Stream<SingleTransactionRecord>> {
    private final SoftwareVersion version;
    private final InitTrigger initTrigger;
    private final RecordListBuilder recordListBuilder;
    private final SkipHandleProcess skipHandleProcess;
    private final SRPHandleProcess defaultHandleProcess;
    private final GenesisHandleProcess genesisHandleProcess;
    final UserTransactionComponent userTxn;
    private final BlockRecordManager blockRecordManager;

    @Inject
    public ProcessRunner(
            @NonNull final SoftwareVersion version,
            @NonNull final InitTrigger initTrigger,
            @NonNull final RecordListBuilder recordListBuilder,
            @NonNull final SkipHandleProcess skipHandleProcess,
            @NonNull final SRPHandleProcess defaultHandleProcess,
            final GenesisHandleProcess genesisHandleProcess,
            @NonNull final UserTransactionComponent userTxn,
            final BlockRecordManager blockRecordManager) {
        this.version = version;
        this.initTrigger = initTrigger;
        this.recordListBuilder = recordListBuilder;
        this.skipHandleProcess = skipHandleProcess;
        this.defaultHandleProcess = defaultHandleProcess;
        this.genesisHandleProcess = genesisHandleProcess;
        this.userTxn = userTxn;
        this.blockRecordManager = blockRecordManager;
    }

    @Override
    public Stream<SingleTransactionRecord> get() {
        if (isOlderSoftwareEvent()) {
            skipHandleProcess.processUserTransaction(userTxn);
        } else {
            if (blockRecordManager.consTimeOfLastHandledTxn().equals(Instant.EPOCH)) {
                genesisHandleProcess.processUserTransaction(userTxn);
            }
            defaultHandleProcess.processUserTransaction(userTxn);
        }
        return recordListBuilder.build().records().stream();
    }

    private boolean isOlderSoftwareEvent() {
        return this.initTrigger != EVENT_STREAM_RECOVERY
                && version.compareTo(userTxn.platformEvent().getSoftwareVersion()) > 0;
    }
}
