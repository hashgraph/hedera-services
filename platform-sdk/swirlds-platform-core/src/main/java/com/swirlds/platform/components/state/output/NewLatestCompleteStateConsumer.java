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

import com.swirlds.platform.state.signed.SignedStateWrapper;

/**
 * There is a new, most up-to-date and complete signed state.
 * <p>
 * The state within the {@link SignedStateWrapper} holds a reservation. The wiring layer releases the
 * {@link SignedStateWrapper} after all consumers have completed.
 */
@FunctionalInterface
public interface NewLatestCompleteStateConsumer {

    /**
     * There is a new latest complete signed state.
     * <p>
     * The signed state holds a reservation for the duration of this call. Implementers must not release this
     * reservation.
     *
     * @param signedStateWrapper the wrapped signed state
     */
    void newLatestCompleteStateEvent(final SignedStateWrapper signedStateWrapper);
}
