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

package com.hedera.node.app.spi.state;

import com.swirlds.common.system.DualState;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;

/**
 * Provides write methods for modifying underlying data storage mechanisms for
 * working with freeze states.
 */
public class WritableFreezeStore extends ReadableFreezeStoreImpl {
    /**
     * Create a new {@link WritableFreezeStore} instance.
     *
     * @param dualState The state to use.
     */
    public WritableFreezeStore(@NonNull final DualState dualState) {
        super(dualState);
    }

    /**
     * Sets the freeze time.
     *
     * @param freezeTime the freeze time to set; if null, clears the freeze time
     */
    public void freezeTime(@Nullable final Instant freezeTime) {
        dualState.setFreezeTime(freezeTime);
    }
}
