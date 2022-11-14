/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.api.implementation.state;

import com.hedera.node.app.spi.Service;
import com.hedera.node.app.spi.state.States;
import com.swirlds.common.AutoCloseableNonThrowing;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.utility.AutoCloseableWrapper;
import java.util.Objects;
import org.apache.commons.lang3.NotImplementedException;

/** The root of all merkle state for the Hedera application. */
public class HederaState implements AutoCloseableNonThrowing {

    private final AutoCloseableWrapper<SwirldState> swirldState;

    /**
     * Constructor of {@code HederaState}
     *
     * @param swirldState underlying {@link SwirldState}, wrapped in an {@link AutoCloseable}
     */
    public HederaState(final AutoCloseableWrapper<SwirldState> swirldState) {
        this.swirldState = swirldState;
    }

    /**
     * Get the {@link States} associated with a specific {@link Service}
     *
     * @param clazz {@link Class} of the {@code Service}
     * @return A non-{@code null} {@code States} instance with access to all state of the service
     */
    public States getServiceStates(final Class<? extends Service> clazz) {
        Objects.requireNonNull(clazz);

        throw new NotImplementedException();
    }

    @Override
    public void close() {
        swirldState.close();
    }
}
