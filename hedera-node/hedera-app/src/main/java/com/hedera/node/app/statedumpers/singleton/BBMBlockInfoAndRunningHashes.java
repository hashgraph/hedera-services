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

package com.hedera.node.app.statedumpers.singleton;

import com.hedera.node.app.statedumpers.legacy.RichInstant;
import com.swirlds.common.crypto.Hash;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

public record BBMBlockInfoAndRunningHashes(
        long lastBlockNumber,
        @NonNull String blockHashes,
        @Nullable RichInstant consTimeOfLastHandledTxn,
        boolean migrationRecordsStreamed,
        @Nullable RichInstant firstConsTimeOfCurrentBlock,
        long entityId,
        @Nullable Hash runningHash,
        @Nullable Hash nMinus1RunningHash,
        @Nullable Hash nMinus2RunningHash,
        @Nullable Hash nMinus3RunningHash) {}
