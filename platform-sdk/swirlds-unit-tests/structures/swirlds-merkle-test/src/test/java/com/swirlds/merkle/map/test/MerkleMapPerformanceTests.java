/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.merkle.map.test;

import com.swirlds.merkle.map.test.util.KeyValueProvider;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.provider.Arguments;

@DisplayName("MerkleMap Performance Tests")
public class MerkleMapPerformanceTests extends MerkleMapTests {

    @Override
    protected Stream<Arguments> buildArguments() {
        final List<Arguments> arguments = new ArrayList<>();
        for (final KeyValueProvider keyValueProvider : KeyValueProvider.values()) {
            arguments.add(Arguments.of(0, keyValueProvider));
            arguments.add(Arguments.of(262_144, keyValueProvider));
        }

        return arguments.stream();
    }

    @Override
    protected Stream<Arguments> buildNumberOfModifications() {
        final List<Arguments> arguments = new ArrayList<>();
        arguments.add(Arguments.of(1));
        arguments.add(Arguments.of(5));
        arguments.add(Arguments.of(8));
        arguments.add(Arguments.of(10));
        return arguments.stream();
    }
}
