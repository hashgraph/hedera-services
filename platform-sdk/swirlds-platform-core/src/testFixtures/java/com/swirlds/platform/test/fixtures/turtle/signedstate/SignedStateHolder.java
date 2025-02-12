/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.fixtures.turtle.signedstate;

import com.swirlds.platform.state.signed.ReservedSignedState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * A test component collecting state signs produced by the {@link com.swirlds.platform.state.signed.StateSignatureCollector}
 */
public interface SignedStateHolder {

    /**
     * Intercept the signed states produced by the StateSignatureCollector and adds them to a collection.
     *
     * @param signedStates the signed state coming from the StateSignatureCollector
     */
    void interceptSignedStates(final List<ReservedSignedState> signedStates);

    /**
     * Clear the internal state of this collector.
     *
     * @param ignored ignored trigger object
     */
    void clear(@NonNull final Object ignored);
}
