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

package com.hedera.node.app.blocks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.blocks.schemas.V0560BlockStreamSchema;
import com.swirlds.state.lifecycle.SchemaRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class BlockStreamServiceTest {
    @Mock
    private SchemaRegistry schemaRegistry;

    private final BlockStreamService subject = new BlockStreamService();

    @Test
    void serviceNameAsExpected() {
        assertThat(subject.getServiceName()).isEqualTo("BlockStreamService");
    }

    @Test
    void enabledSubjectRegistersV0540Schema() {
        subject.registerSchemas(schemaRegistry);

        verify(schemaRegistry).register(argThat(s -> s instanceof V0560BlockStreamSchema));
    }
}
