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

package com.swirlds.common.wiring.component;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.wires.input.InputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.Test;

public class WiringComponentTests {

    private interface FooBarBaz extends WiringComponent<Long> {
        long handleFoo(int foo);

        long handleBar(boolean bar);

        void handleBaz(@NonNull String baz);
    }

    private static class FooBarBazImpl implements FooBarBaz {

        private long runningValue = 0;

        @Override
        public long handleFoo(final int foo) {
            return runningValue += foo;
        }

        @Override
        public long handleBar(final boolean bar) {
            return runningValue *= bar ? 1 : -1;
        }

        @Override
        public void handleBaz(@NonNull final String baz) {
            runningValue *= baz.hashCode();
        }
    }

    @Test
    void test() {
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final WiringModel wiringModel =
                WiringModel.create(platformContext, platformContext.getTime(), ForkJoinPool.commonPool());

        final WiringComponentConfiguration configuration = new WiringComponentConfiguration();

        final WiringComponentBuilder<FooBarBaz, Long> builder =
                new WiringComponentBuilder<>(wiringModel, FooBarBaz.class, configuration);

        final InputWire<Integer> fooInput = builder.getInputWire(FooBarBaz::handleFoo);
        final InputWire<Boolean> barInput = builder.getInputWire(FooBarBaz::handleBar);
        final InputWire<String> bazInput = builder.getInputWire(FooBarBaz::handleBaz);

        final OutputWire<Long> output = builder.getOutputWire();

        builder.bind(new FooBarBazImpl());
    }
}
