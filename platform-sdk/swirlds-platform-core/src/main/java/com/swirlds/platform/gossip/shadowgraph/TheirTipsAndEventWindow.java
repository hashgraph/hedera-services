/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.gossip.shadowgraph;

import com.swirlds.common.crypto.Hash;
import com.swirlds.platform.consensus.NonAncientEventWindow;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * The tips and event window of the sync peer. This is the first thing sent/received during a sync (after protocol
 * negotiation).
 */
public record TheirTipsAndEventWindow(@NonNull NonAncientEventWindow eventWindow, @NonNull List<Hash> tips) {}
