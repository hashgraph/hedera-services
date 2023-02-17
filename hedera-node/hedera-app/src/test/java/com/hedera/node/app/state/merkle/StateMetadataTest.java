/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.state.merkle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;

import com.hedera.node.app.spi.fixtures.state.TestSchema;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.StateDefinition;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class StateMetadataTest extends MerkleTestBase {

    private Schema schema;
    private StateDefinition<Long, String> def;

    public static Stream<Arguments> illegalServiceNames() {
        return StateUtilsTest.illegalIdentifiers();
    }

    public static Stream<Arguments> legalServiceNames() {
        return StateUtilsTest.legalIdentifiers();
    }

    @BeforeEach
    void setUp() {
        setupSpaceMerkleMap();
        schema = new TestSchema(1);
        def = spaceMetadata.stateDefinition();
    }

    @Test
    @DisplayName("Null service name throws in the constructor")
    void nullServiceNameThrows() {
        //noinspection DataFlowIssue
        assertThatThrownBy(() -> new StateMetadata<>(null, schema, def));
    }

    @Test
    @DisplayName("Null schema throws in the constructor")
    void nullSchemaThrows() {
        //noinspection DataFlowIssue
        assertThatThrownBy(() -> new StateMetadata<>(FIRST_SERVICE, null, def));
    }

    @Test
    @DisplayName("Null state definition throws in the constructor")
    void nullStateDefinitionThrows() {
        //noinspection DataFlowIssue
        assertThatThrownBy(() -> new StateMetadata<>(FIRST_SERVICE, schema, null));
    }

    @ParameterizedTest
    @MethodSource("illegalServiceNames")
    @DisplayName("Service names with illegal characters throw an exception")
    void invalidStateKey(final String serviceName) {
        assertThatThrownBy(() -> new StateMetadata<>(serviceName, schema, def))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @MethodSource("legalServiceNames")
    @DisplayName("Service names with legal characters are valid")
    void validStateKey(final String serviceName) {
        final var md = new StateMetadata<>(serviceName, schema, def);
        assertThat(md.serviceName()).isEqualTo(serviceName);
    }

    // verify the different generated classIDs are right

    @Test
    @DisplayName("inMemoryValueClassId is as expected")
    void inMemoryValueClassId() {
        final var md = new StateMetadata<>(FIRST_SERVICE, schema, def);
        final var expected = StateUtils.computeClassId(md, "InMemoryValue");
        assertThat(expected).isEqualTo(md.inMemoryValueClassId());
    }

    @Test
    @DisplayName("onDiskKeyClassId is as expected")
    void onDiskKeyClassId() {
        final var md = new StateMetadata<>(FIRST_SERVICE, schema, def);
        final var expected = StateUtils.computeClassId(md, "OnDiskKey");
        assertThat(expected).isEqualTo(md.onDiskKeyClassId());
    }

    @Test
    @DisplayName("onDiskValueClassId is as expected")
    void onDiskValueClassId() {
        final var md = new StateMetadata<>(FIRST_SERVICE, schema, def);
        final var expected = StateUtils.computeClassId(md, "OnDiskValue");
        assertThat(expected).isEqualTo(md.onDiskValueClassId());
    }

    @Test
    @DisplayName("onDiskKeySerializerClassId is as expected")
    void onDiskKeySerializerClassId() {
        final var md = new StateMetadata<>(FIRST_SERVICE, schema, def);
        final var expected = StateUtils.computeClassId(md, "OnDiskKeySerializer");
        assertThat(expected).isEqualTo(md.onDiskKeySerializerClassId());
    }

    @Test
    @DisplayName("onDiskValueSerializerClassId is as expected")
    void onDiskValueSerializerClassId() {
        final var md = new StateMetadata<>(FIRST_SERVICE, schema, def);
        final var expected = StateUtils.computeClassId(md, "OnDiskValueSerializer");
        assertThat(expected).isEqualTo(md.onDiskValueSerializerClassId());
    }
}
