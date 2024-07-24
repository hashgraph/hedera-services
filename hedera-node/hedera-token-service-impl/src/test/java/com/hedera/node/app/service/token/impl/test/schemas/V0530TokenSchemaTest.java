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

package com.hedera.node.app.service.token.impl.test.schemas;

import static org.assertj.core.api.Assertions.assertThat;

import com.hedera.hapi.node.base.PendingAirdropId;
import com.hedera.hapi.node.base.PendingAirdropValue;
import com.hedera.node.app.service.token.impl.schemas.V0530TokenSchema;
import com.swirlds.state.spi.StateDefinition;
import java.util.Comparator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class V0530TokenSchemaTest {

    private final V0530TokenSchema subject = new V0530TokenSchema();

    @Test
    @DisplayName("verify states to create")
    void verifyStatesToCreate() {
        var sortedResult = subject.statesToCreate().stream()
                .sorted(Comparator.comparing(StateDefinition::stateKey))
                .toList();

        final var firstStateDef = sortedResult.getFirst();
        assertThat(firstStateDef.stateKey()).isEqualTo("PENDING_AIRDROPS");
        assertThat(firstStateDef.keyCodec()).isEqualTo(PendingAirdropId.PROTOBUF);
        assertThat(firstStateDef.valueCodec()).isEqualTo(PendingAirdropValue.PROTOBUF);
    }
}
