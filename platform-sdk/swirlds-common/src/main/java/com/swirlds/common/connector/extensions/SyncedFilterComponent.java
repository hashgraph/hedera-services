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
import com.swirlds.common.connector.Publisher;
import java.util.function.Predicate;

/**
 * A component that filters data based on a predicate.
 *
 * @param <DATA>
 */
public class SyncedFilterComponent<DATA> {

    private final Input<DATA> input;

    private Publisher<DATA> output;

    public SyncedFilterComponent(ConnectionFactory wireFactory, Predicate<DATA> filter) {
        output = wireFactory.createPublisher();
        this.input = wireFactory.createSyncedInput(d -> {
            if (filter.test(d)) {
                output.publish(d);
            }
        });
    }

    public Input<DATA> getInput() {
        return input;
    }


}
