/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.components.state.output;

import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Invoked when a signed state fails to collect sufficient signatures before being ejected from memory.
 * <p>
 * The state within the {@link ReservedSignedState} holds a reservation. The wiring layer must release the
 * {@link ReservedSignedState} after all consumers have completed.
 */
@FunctionalInterface
public interface StateLacksSignaturesConsumer {

    /**
     * A signed state is about to be ejected from memory and has not collected enough signatures to be complete.
     *
     * @param signedState the signed state
     */
    void stateLacksSignatures(@NonNull SignedState signedState);
}
