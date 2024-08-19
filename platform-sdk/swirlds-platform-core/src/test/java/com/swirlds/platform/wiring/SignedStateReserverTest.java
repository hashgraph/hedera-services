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

package com.swirlds.platform.wiring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.common.utility.ValueReference;
import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.model.WiringModelBuilder;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.schedulers.builders.TaskSchedulerType;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.platform.crypto.SignatureVerifier;
import com.swirlds.platform.state.MerkleRoot;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SignedStateReserverTest {

    @Test
    void basicTest() {
        final int numConsumers = 3;

        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final SignedState signedState = new SignedState(
                platformContext,
                Mockito.mock(SignatureVerifier.class),
                Mockito.mock(MerkleRoot.class),
                "create",
                false,
                false,
                false);

        final WiringModel model = WiringModelBuilder.create(platformContext).build();
        final TaskScheduler<ReservedSignedState> taskScheduler = model.schedulerBuilder("scheduler")
                .withType(TaskSchedulerType.DIRECT)
                .build()
                .cast();
        final OutputWire<ReservedSignedState> outputWire =
                taskScheduler.getOutputWire().buildAdvancedTransformer(new SignedStateReserver("reserver"));
        final BindableInputWire<ReservedSignedState, ReservedSignedState> inputWire =
                taskScheduler.buildInputWire("in");
        inputWire.bind(s -> s);

        final List<ValueReference<ReservedSignedState>> consumers = Stream.generate(
                        ValueReference<ReservedSignedState>::new)
                .limit(numConsumers)
                .toList();
        IntStream.range(0, consumers.size())
                .forEach(i -> outputWire.solderTo("name_" + i, "consumer input", consumers.get(i)::setValue));

        final ReservedSignedState state = signedState.reserve("main");
        assertFalse(state.isClosed(), "we just reserved it, so it should not be closed");
        assertEquals(1, signedState.getReservationCount(), "the reservation count should be 1");
        inputWire.put(state);
        assertTrue(state.isClosed(), "the reserver should have closed our reservation");
        consumers.forEach(c -> assertFalse(c.getValue().isClosed(), "the consumer should not have closed its state"));
        assertEquals(
                numConsumers, signedState.getReservationCount(), "there should be a reservation for each consumer");
        consumers.forEach(c -> c.getValue().close());
    }
}
