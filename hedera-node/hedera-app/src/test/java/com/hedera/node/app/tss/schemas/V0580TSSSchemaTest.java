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

package com.hedera.node.app.tss.schemas;

import static com.hedera.node.app.tss.schemas.V0580TssBaseSchema.TSS_ENCRYPTION_KEYS_KEY;
import static com.hedera.node.app.tss.schemas.V0580TssBaseSchema.TSS_STATUS_KEY;
import static org.assertj.core.api.Assertions.assertThat;

import com.swirlds.state.lifecycle.StateDefinition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link V0580TssBaseSchema}.
 */
public class V0580TSSSchemaTest {
    private V0580TssBaseSchema subject;

    @BeforeEach
    void setUp() {
        subject = new V0580TssBaseSchema();
    }

    @Test
    void registersExpectedSchema() {
        final var statesToCreate = subject.statesToCreate();
        assertThat(statesToCreate.size()).isEqualTo(2);
        final var iter =
                statesToCreate.stream().map(StateDefinition::stateKey).sorted().iterator();
        // Assert that statesToCreate contains the expected keys
        while (iter.hasNext()) {
            final var stateKey = iter.next();
            if (!stateKey.equals(TSS_ENCRYPTION_KEYS_KEY) && !stateKey.equals(TSS_STATUS_KEY)) {
                throw new IllegalStateException("Unexpected state key: " + stateKey);
            }
        }
    }
}
