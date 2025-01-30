/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.swirlds.component.framework.schedulers;

import static com.swirlds.component.framework.schedulers.builders.TaskSchedulerType.NO_OP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.component.framework.model.WiringModel;
import com.swirlds.component.framework.model.WiringModelBuilder;
import com.swirlds.component.framework.schedulers.builders.TaskSchedulerType;
import com.swirlds.component.framework.wires.SolderType;
import com.swirlds.component.framework.wires.input.BindableInputWire;
import com.swirlds.component.framework.wires.output.OutputWire;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class NoOpTaskSchedulerTests {

    @Test
    void nothingHappensTest() {
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();
        final WiringModel model = WiringModelBuilder.create(platformContext).build();

        final TaskScheduler<List<Integer>> realFakeScheduler =
                model.<List<Integer>>schedulerBuilder("A").withType(NO_OP).build();

        final AtomicBoolean shouldAlwaysBeFalse = new AtomicBoolean(false);

        final BindableInputWire<Integer, List<Integer>> inA = realFakeScheduler.buildInputWire("inA");
        inA.bindConsumer((value) -> shouldAlwaysBeFalse.set(true));

        final BindableInputWire<Integer, List<Integer>> inB = realFakeScheduler.buildInputWire("inB");
        inB.bind((value) -> {
            shouldAlwaysBeFalse.set(true);
            return List.of(1, 2, 3);
        });

        final OutputWire<List<Integer>> transformer = realFakeScheduler
                .getOutputWire()
                .buildTransformer("transformer", "transformer input", (value) -> {
                    shouldAlwaysBeFalse.set(true);
                    return List.of(1, 2, 3);
                });

        final OutputWire<List<Integer>> filter = realFakeScheduler
                .getOutputWire()
                .buildFilter("filter", "filter input", (value) -> {
                    shouldAlwaysBeFalse.set(true);
                    return true;
                });

        final OutputWire<Integer> splitter =
                realFakeScheduler.getOutputWire().buildSplitter("splitter", "splitter input");
        splitter.solderTo("handler", "handler input", value -> shouldAlwaysBeFalse.set(true));

        // Solder the fake scheduler to a real one. No data should be passed.
        final TaskScheduler<Void> realScheduler = model.<Void>schedulerBuilder("B")
                .withType(TaskSchedulerType.DIRECT)
                .build();

        final BindableInputWire<List<Integer>, Void> realInA = realScheduler.buildInputWire("realInA");
        realInA.bindConsumer((value) -> shouldAlwaysBeFalse.set(true));
        realFakeScheduler.getOutputWire().solderTo(realInA, SolderType.PUT);
        realFakeScheduler.getOutputWire().solderTo(realInA, SolderType.OFFER);
        realFakeScheduler.getOutputWire().solderTo(realInA, SolderType.INJECT);
        transformer.solderTo(realInA, SolderType.PUT);
        transformer.solderTo(realInA, SolderType.OFFER);
        transformer.solderTo(realInA, SolderType.INJECT);
        filter.solderTo(realInA, SolderType.PUT);
        filter.solderTo(realInA, SolderType.OFFER);
        filter.solderTo(realInA, SolderType.INJECT);

        final BindableInputWire<Integer, Void> realInB = realScheduler.buildInputWire("realInB");
        realInB.bindConsumer((value) -> shouldAlwaysBeFalse.set(true));
        splitter.solderTo(realInB, SolderType.PUT);
        splitter.solderTo(realInB, SolderType.OFFER);
        splitter.solderTo(realInB, SolderType.INJECT);

        inA.put(1);
        assertTrue(inA.offer(1));
        inA.inject(1);

        inB.put(1);
        assertTrue(inB.offer(1));
        inB.inject(1);

        assertFalse(shouldAlwaysBeFalse.get());
        assertEquals(0, realFakeScheduler.getUnprocessedTaskCount());
    }
}
