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

package com.hedera.node.app.state;

import com.swirlds.platform.system.events.Event;
import com.swirlds.state.State;
import edu.umd.cs.findbugs.annotations.NonNull;

/** Listener invoked whenever an event is ready for pre-handle */
@FunctionalInterface
public interface PreHandleListener {
    void onPreHandle(@NonNull Event event, @NonNull State state);
}
