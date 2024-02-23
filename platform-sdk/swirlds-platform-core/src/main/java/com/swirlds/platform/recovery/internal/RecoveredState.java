/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.recovery.internal;

import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.state.signed.ReservedSignedState;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Used during post-consensus event stream recovery. Stores the state after transactions have been applied to it and
 * the proposed judge for that state.
 * @param state a state that has had transactions applied to it
 * @param judge the proposed judge for the state
 */
public record RecoveredState(@NonNull ReservedSignedState state, @NonNull GossipEvent judge) {}
