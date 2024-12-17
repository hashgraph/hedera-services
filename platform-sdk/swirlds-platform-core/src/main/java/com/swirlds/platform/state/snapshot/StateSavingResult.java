/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.snapshot;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

/**
 * The result of a successful state saving operation.
 *
 * @param round                         the round of the state saved to disk
 * @param freezeState                   true if the state was freeze state, false otherwise
 * @param consensusTimestamp            the consensus timestamp of the state saved to disk
 * @param oldestMinimumGenerationOnDisk as part of the state saving operation, old states are deleted from disk. This
 *                                      value represents the minimum generation non-ancient of the oldest state on disk
 */
public record StateSavingResult(
        long round, boolean freezeState, @NonNull Instant consensusTimestamp, long oldestMinimumGenerationOnDisk) {}
