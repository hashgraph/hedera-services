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

package com.hedera.node.app.services;

import static com.hedera.node.app.spi.fixtures.state.TestSchema.CURRENT_VERSION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.Timestamp;
import com.hedera.node.app.spi.fixtures.TestService;
import com.hedera.node.app.spi.fixtures.state.TestSchema;
import com.hedera.node.app.spi.state.StateDefinition;
import com.hedera.node.app.spi.workflows.record.GenesisRecordsBuilder;
import com.hedera.node.app.version.HederaSoftwareVersion;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class ServicesRegistryImplTest {

    @Mock
    ConstructableRegistry cr;

    @Mock
    GenesisRecordsBuilder genesisRecords;

    @DisplayName("The constructable registry cannot be null")
    @Test
    void nullConstructableRegistryThrows() {
        //noinspection DataFlowIssue
        assertThatThrownBy(() -> new ServicesRegistryImpl(null, genesisRecords))
                .isInstanceOf(NullPointerException.class);
    }

    @DisplayName("The genesis record builder cannot be null")
    @Test
    void nullGenesisRecordsThrows() {
        //noinspection DataFlowIssue
        assertThatThrownBy(() -> new ServicesRegistryImpl(cr, null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void registerCallsTheConstructableRegistry() throws ConstructableRegistryException {
        final var registry = new ServicesRegistryImpl(cr, genesisRecords);
        registry.register(
                TestService.newBuilder()
                        .name("registerCallsTheConstructableRegistryTest")
                        .schema(TestSchema.newBuilder()
                                .minorVersion(1)
                                .stateToCreate(StateDefinition.singleton("Singleton", Timestamp.JSON))
                                .build())
                        .build(),
                new HederaSoftwareVersion(CURRENT_VERSION, CURRENT_VERSION, 0));
        //noinspection removal
        verify(cr, atLeastOnce()).registerConstructable(any());
    }

    @Test
    void registrationsAreSortedByName() {
        final var registry = new ServicesRegistryImpl(cr, genesisRecords);
        registry.register(
                TestService.newBuilder().name("B").build(),
                new HederaSoftwareVersion(CURRENT_VERSION, CURRENT_VERSION, 0));
        registry.register(
                TestService.newBuilder().name("C").build(),
                new HederaSoftwareVersion(CURRENT_VERSION, CURRENT_VERSION, 0));
        registry.register(
                TestService.newBuilder().name("A").build(),
                new HederaSoftwareVersion(CURRENT_VERSION, CURRENT_VERSION, 0));

        final var registrations = registry.registrations();
        assertThat(registrations.stream().map(r -> r.service().getServiceName()))
                .containsExactly("A", "B", "C");
    }
}
