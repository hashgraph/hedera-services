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

package com.swirlds.platform.wiring;

import com.swirlds.platform.state.signed.ReservedSignedState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Manages reservations of a signed state when it needs to be passed to one or more consumers. Whenever a state is
 * passed to this instance, the assumption will be that it reserved for the reserver, and it is responsible for
 * releasing it. The state is passed to downstream consumers with the same assumption: each consumer will get a state
 * with a reservation for itself, and it is responsible for releasing it.
 */
public class SignedStateReserver implements Consumer<ReservedSignedState> {
    private final List<ConsumerWrapper> consumers = new ArrayList<>();

    /**
     * Convenience method to create a new instance.
     *
     * @return a new instance
     */
    public static SignedStateReserver create() {
        return new SignedStateReserver();
    }

    /**
     * Reserves a state for each downstream consumer and passes it to them.
     *
     * @param reservedSignedState the state to propagate
     */
    @Override
    public void accept(@NonNull final ReservedSignedState reservedSignedState) {
        for (final ConsumerWrapper consumer : consumers) {
            final ReservedSignedState newReservation = reservedSignedState.getAndReserve(consumer.name());
            consumer.consume().accept(newReservation);
        }
        reservedSignedState.close();
    }

    /**
     * Adds a consumer to the list of consumers that will receive a reserved state.
     *
     * @param consumer the consumer to add
     * @return this instance
     */
    public SignedStateReserver addConsumer(@NonNull final Consumer<ReservedSignedState> consumer) {
        return addConsumer(consumer, Objects.requireNonNull(consumer).getClass().getSimpleName());
    }

    /**
     * Adds a consumer to the list of consumers that will receive a reserved state.
     *
     * @param consumer the consumer to add
     * @param name     the name of the consumer
     * @return this instance
     */
    public SignedStateReserver addConsumer(
            @NonNull final Consumer<ReservedSignedState> consumer, @NonNull final String name) {
        consumers.add(new ConsumerWrapper(Objects.requireNonNull(consumer), Objects.requireNonNull(name)));
        return this;
    }

    private record ConsumerWrapper(@NonNull Consumer<ReservedSignedState> consume, @NonNull String name) {}
}
