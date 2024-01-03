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

package com.swirlds.platform.gossip.sync.turbo;

import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.gossip.shadowgraph.Generations;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * Contains data sent during phase A of a sync iteration.
 *
 * @param generations the generations sent during this iteration
 * @param tips        the tips sent during this iteration
 */
public record TipsAndGenerations(@NonNull Generations generations, @NonNull List<Hash> tips) {}
