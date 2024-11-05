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

package com.swirlds.platform.state.service;

import static com.swirlds.platform.state.service.PlatformStateService.PLATFORM_STATE_SERVICE;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.platform.state.PlatformState;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.platform.state.service.schemas.V0540PlatformStateSchema;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.SchemaRegistry;
import com.swirlds.state.merkle.MerkleStateRoot;
import com.swirlds.state.merkle.singleton.SingletonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlatformStateServiceTest {
    @Mock
    private SchemaRegistry registry;

    @Mock
    private MerkleNode node;

    @Mock
    private MerkleStateRoot root;

    @Mock
    private SoftwareVersion version;

    @Mock
    private com.swirlds.platform.state.PlatformState legacyState;

    @Mock
    private SingletonNode<PlatformState> platformState;

    @Test
    void registersOneSchema() {
        final ArgumentCaptor<Schema> captor = ArgumentCaptor.forClass(Schema.class);
        given(registry.register(captor.capture())).willReturn(registry);
        PLATFORM_STATE_SERVICE.registerSchemas(registry);
        assertEquals(1, captor.getAllValues().size());
    }

    @Test
    void emptyRootIsAtGenesis() {
        given(root.getNumberOfChildren()).willReturn(0);
        assertNull(PLATFORM_STATE_SERVICE.creationVersionOf(root));
    }

    @Test
    void rootWithNoPlatformState_throwsException() {
        given(root.getNumberOfChildren()).willReturn(1);
        given(root.findNodeIndex(PlatformStateService.NAME, V0540PlatformStateSchema.PLATFORM_STATE_KEY))
                .willReturn(-1);

        assertThrows(IllegalStateException.class, () -> PLATFORM_STATE_SERVICE.creationVersionOf(root));
    }

    @Test
    void rootWithPlatformStateGetsVersionFromPlatformState() {
        given(root.getNumberOfChildren()).willReturn(1);
        given(root.findNodeIndex(PlatformStateService.NAME, V0540PlatformStateSchema.PLATFORM_STATE_KEY))
                .willReturn(0);
        given(root.getChild(0)).willReturn(platformState);
        given(platformState.getValue())
                .willReturn(PlatformState.newBuilder()
                        .creationSoftwareVersion(SemanticVersion.DEFAULT)
                        .build());
        assertSame(SemanticVersion.DEFAULT, PLATFORM_STATE_SERVICE.creationVersionOf(root));
    }
}
