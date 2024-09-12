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

package com.hedera.node.app.blocks;

import com.hedera.pbj.runtime.io.buffer.Bytes;

/**
 * A simple record to hold the starting state info when node start at Genesis, Restart or Reconnect.
 * This is needed because the stateHash is needed to construct {@link com.hedera.hapi.block.stream.BlockProof}
 * for first round after node started.
 * @param stateHash the stateHash after genesis or restart or reconnect
 * @param roundNum the round number of the state
 */
public record StartingStateInfo(Bytes stateHash, long roundNum) {}
