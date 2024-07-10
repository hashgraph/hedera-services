/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.swirlds.common.connector.extensions;

import com.swirlds.common.connector.ConnectionFactory;
import com.swirlds.common.connector.Input;
import com.swirlds.common.connector.Output;
import com.swirlds.common.connector.Publisher;
import java.util.function.Function;

public class SyncedTransformer<IN, OUT> {

    private final Input<IN> input;

    private final Publisher<OUT> output;

    public SyncedTransformer(ConnectionFactory wireFactory, Function<IN, OUT> transformer) {
        final Publisher<IN> inputpublisher = wireFactory.createPublisher();
        output = wireFactory.createPublisher();
        inputpublisher.connect(d -> output.publish(transformer.apply(d)));
        this.input = wireFactory.createSyncedInput(d -> inputpublisher.publish(d));
    }

    public Input<IN> getInput() {
        return input;
    }

    public Output<OUT> getOutput() {
        return output;
    }
}
