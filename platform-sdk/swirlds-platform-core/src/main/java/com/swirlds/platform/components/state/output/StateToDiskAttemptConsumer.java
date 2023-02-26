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

import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateWrapper;
import java.nio.file.Path;

/**
 * Invoked when an attempt to write a state to disk is made, either successfully or unsuccessfully.
 * <p>
 * The state within the {@link SignedStateWrapper} holds a reservation. The wiring layer must release the
 * {@link SignedStateWrapper} after all consumers have completed.
 */
@FunctionalInterface
public interface StateToDiskAttemptConsumer {

    /**
     * Invoked when a state is attempted to be written to disk.
     * <p>
     * The signed state holds a reservation for the duration of this call. Implementers must not release this
     * reservation.
     *
     * @param signedStateWrapper A wrapper with the {@link SignedState} attempted to be written to disk
     * @param directory          The directory where the state was attempted to be written
     * @param success            {@code true} if the attempt was successful, {@code false} otherwise
     */
    void stateToDiskAttempt(SignedStateWrapper signedStateWrapper, Path directory, boolean success);
}
