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

package com.swirlds.platform.state.signed;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

/**
 * The {@link SignedStateSentinel} is responsible for observing the lifespans of signed states, and taking action if a
 * state suspected of a memory leak is observed.
 */
public interface SignedStateSentinel {
    /**
     * Check the maximum age of signed states, and take action if a really old state is observed.
     *
     * @param now the current time
     */
    void checkSignedStates(@NonNull final Instant now);
}
