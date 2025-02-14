// SPDX-License-Identifier: Apache-2.0
package com.swirlds.platform.state.service;

import static com.swirlds.platform.state.service.PlatformStateService.PLATFORM_STATE_SERVICE;
import static com.swirlds.platform.state.service.schemas.V0540PlatformStateSchema.PLATFORM_STATE_KEY;
import static com.swirlds.platform.test.fixtures.state.TestPlatformStateFacade.TEST_PLATFORM_STATE_FACADE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.platform.state.PlatformState;
import com.swirlds.platform.state.service.schemas.V0540PlatformStateSchema;
import com.swirlds.platform.state.service.schemas.V059RosterLifecycleTransitionSchema;
import com.swirlds.state.lifecycle.Schema;
import com.swirlds.state.lifecycle.SchemaRegistry;
import com.swirlds.state.merkle.MerkleStateRoot;
import com.swirlds.state.merkle.singleton.ReadableSingletonStateImpl;
import com.swirlds.state.merkle.singleton.SingletonNode;
import com.swirlds.state.spi.EmptyReadableStates;
import com.swirlds.state.test.fixtures.MapReadableStates;
import java.util.Map;
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
    private MerkleStateRoot root;

    @Mock
    private SingletonNode<PlatformState> platformState;

    @Test
    void registersOneSchema() {
        final ArgumentCaptor<Schema> captor = ArgumentCaptor.forClass(Schema.class);
        given(registry.register(captor.capture())).willReturn(registry);
        PLATFORM_STATE_SERVICE.registerSchemas(registry);
        final var schemas = captor.getAllValues();
        assertEquals(2, schemas.size());
        assertInstanceOf(V0540PlatformStateSchema.class, schemas.getFirst());
        assertInstanceOf(V059RosterLifecycleTransitionSchema.class, schemas.getLast());
    }

    @Test
    void emptyRootIsAtGenesis() {
        given(root.getReadableStates(PlatformStateService.NAME)).willReturn(EmptyReadableStates.INSTANCE);
        given(root.findNodeIndex(PlatformStateService.NAME, PLATFORM_STATE_KEY)).willReturn(-1);
        assertNull(TEST_PLATFORM_STATE_FACADE.creationSemanticVersionOf(root));
    }

    @Test
    void rootWithPlatformStateGetsVersionFromPlatformState() {
        MapReadableStates readableStates = new MapReadableStates(
                Map.of(PLATFORM_STATE_KEY, new ReadableSingletonStateImpl<>(PLATFORM_STATE_KEY, platformState)));
        given(root.getReadableStates(PlatformStateService.NAME)).willReturn(readableStates);
        given(platformState.getValue())
                .willReturn(PlatformState.newBuilder()
                        .creationSoftwareVersion(SemanticVersion.DEFAULT)
                        .build());
        assertSame(SemanticVersion.DEFAULT, TEST_PLATFORM_STATE_FACADE.creationSemanticVersionOf(root));
    }
}
