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

package com.hedera.node.app.spi.state;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.pbj.runtime.Codec;
import com.swirlds.state.merkle.StateDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StateDefinitionTest {
    @Mock
    private Codec<String> mockCodec;

    @Test
    void singletonsCannotBeOnDisk() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StateDefinition<>("KEY", mockCodec, mockCodec, 123, true, true, false));
    }

    @Test
    void onDiskMustHintPositiveNumKeys() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new StateDefinition("KEY", mockCodec, mockCodec, 0, true, false, false));
    }

    @Test
    void nonSingletonRequiresKeySerdes() {
        assertThrows(
                NullPointerException.class, () -> new StateDefinition("KEY", null, mockCodec, 1, true, false, false));
    }

    @Test
    void inMemoryFactoryWorks() {
        assertDoesNotThrow(() -> StateDefinition.inMemory("KEY", mockCodec, mockCodec));
    }

    @Test
    void onDiskFactoryWorks() {
        assertDoesNotThrow(() -> StateDefinition.onDisk("KEY", mockCodec, mockCodec, 123));
    }

    @Test
    void singletonFactoryWorks() {
        assertDoesNotThrow(() -> StateDefinition.singleton("KEY", mockCodec));
    }

    @Test
    void constructorWorks() {
        assertDoesNotThrow(() -> new StateDefinition("KEY", mockCodec, mockCodec, 123, true, false, false));
    }
}
