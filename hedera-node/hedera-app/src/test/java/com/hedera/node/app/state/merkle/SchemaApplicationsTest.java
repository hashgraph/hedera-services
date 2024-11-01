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

package com.hedera.node.app.state.merkle;

import static com.hedera.node.app.state.merkle.SchemaApplicationType.MIGRATION;
import static com.hedera.node.app.state.merkle.SchemaApplicationType.RESTART;
import static com.hedera.node.app.state.merkle.SchemaApplicationType.STATE_DEFINITIONS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.merkle.Schema;
import com.swirlds.state.merkle.StateDefinition;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SchemaApplicationsTest {
    @SuppressWarnings("rawtypes")
    private static final StateDefinition STATE_DEFINITION = StateDefinition.singleton("NUMBER", EntityNumber.PROTOBUF);

    private static final SemanticVersion LATEST_VERSION =
            SemanticVersion.newBuilder().major(3).build();
    private static final SemanticVersion PRECEDING_VERSION =
            SemanticVersion.newBuilder().major(2).build();
    private static final SemanticVersion FIRST_VERSION =
            SemanticVersion.newBuilder().major(1).build();

    @Mock
    private Schema schema;

    @Mock
    private Configuration config;

    private final SchemaApplications subject = new SchemaApplications();

    @Test
    void genesisUseWithNoCreatedOrRemovedStatesIsMigrateOnlyIfNotCurrent() {
        given(schema.getVersion()).willReturn(PRECEDING_VERSION);
        assertThat(subject.computeApplications(null, LATEST_VERSION, schema, config))
                .containsExactly(MIGRATION);
    }

    @Test
    void genesisUseWithCreatedStatesIsStateDefsAndMigrateIfNotCurrent() {
        given(schema.getVersion()).willReturn(PRECEDING_VERSION);
        given(schema.statesToCreate(config)).willReturn(Set.of(STATE_DEFINITION));
        assertThat(subject.computeApplications(null, LATEST_VERSION, schema, config))
                .containsExactly(STATE_DEFINITIONS, MIGRATION);
    }

    @Test
    void genesisUseWithRemovedStatesIsStateDefsAndMigrateIfNotCurrent() {
        given(schema.getVersion()).willReturn(PRECEDING_VERSION);
        given(schema.statesToRemove()).willReturn(Set.of(STATE_DEFINITION.stateKey()));
        assertThat(subject.computeApplications(null, LATEST_VERSION, schema, config))
                .containsExactly(STATE_DEFINITIONS, MIGRATION);
    }

    @Test
    void genesisUseWithNoCreatedOrRemovedStatesIsMigrateAndRestartIfCurrent() {
        given(schema.getVersion()).willReturn(LATEST_VERSION);
        assertThat(subject.computeApplications(null, LATEST_VERSION, schema, config))
                .containsExactly(MIGRATION, RESTART);
    }

    @Test
    void restartUseWithEarlierStateDoesMigrate() {
        given(schema.getVersion()).willReturn(PRECEDING_VERSION);
        assertThat(subject.computeApplications(FIRST_VERSION, LATEST_VERSION, schema, config))
                .containsExactly(MIGRATION);
    }

    @Test
    void restartUseWithSameStateDoesNotMigrate() {
        given(schema.getVersion()).willReturn(PRECEDING_VERSION);
        assertThat(subject.computeApplications(PRECEDING_VERSION, LATEST_VERSION, schema, config))
                .isEmpty();
    }
}
