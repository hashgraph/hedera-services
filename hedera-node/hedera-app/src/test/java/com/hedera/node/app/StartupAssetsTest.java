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

package com.hedera.node.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatCode;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.node.internal.network.Network;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;

public class StartupAssetsTest {

    private static class MockFactory implements StartupAssets.Factory {
        @Override
        public StartupAssets fromInitialConditions(Path workingDir) {
            return mock(StartupAssets.class);
        }
    }

    @Test
    void testFactoryFromInitialConditions() {
        Path workingDir = mock(Path.class);
        MockFactory factory = new MockFactory();
        StartupAssets result = factory.fromInitialConditions(workingDir);
        assertThat(result).isNotNull();
    }

    @Test
    void testMigrationNetworkOrThrowReturnsNetwork() {
        StartupAssets startupAssets = mock(StartupAssets.class);
        Network mockNetwork = mock(Network.class);
        when(startupAssets.migrationNetworkOrThrow()).thenReturn(mockNetwork);
        Network result = startupAssets.migrationNetworkOrThrow();

        assertThat(result).isNotNull();
        assertThat(mockNetwork).isEqualTo(result);
    }

    @Test
    void testMigrationNetworkOrThrowThrowsException() {
        StartupAssets startupAssets = mock(StartupAssets.class);
        when(startupAssets.migrationNetworkOrThrow())
                .thenThrow(new UnsupportedOperationException("Migration not supported"));
        assertThatThrownBy(startupAssets::migrationNetworkOrThrow)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Migration not supported");
    }

    @Test
    void testGenesisNetworkOrThrowReturnsNetwork() {
        StartupAssets startupAssets = mock(StartupAssets.class);
        Network mockNetwork = mock(Network.class);

        when(startupAssets.genesisNetworkOrThrow()).thenReturn(mockNetwork);
        Network result = startupAssets.genesisNetworkOrThrow();
        assertThat(result).isNotNull().isEqualTo(mockNetwork);
    }

    @Test
    void testGenesisNetworkOrThrowThrowsException() {
        StartupAssets startupAssets = mock(StartupAssets.class);
        when(startupAssets.genesisNetworkOrThrow())
                .thenThrow(new UnsupportedOperationException("Genesis network not supported"));
        assertThatThrownBy(startupAssets::genesisNetworkOrThrow)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessage("Genesis network not supported");
    }

    @Test
    void testOverrideNetworkReturnsNetwork() {
        StartupAssets startupAssets = mock(StartupAssets.class);
        Network mockNetwork = mock(Network.class);
        long roundNumber = 123L;
        when(startupAssets.overrideNetwork(roundNumber)).thenReturn(Optional.of(mockNetwork));
        Optional<Network> result = startupAssets.overrideNetwork(roundNumber);
        assertThat(result).isPresent().contains(mockNetwork);
    }

    @Test
    void testOverrideNetworkReturnsEmptyOptional() {
        StartupAssets startupAssets = mock(StartupAssets.class);
        long roundNumber = 123L;
        when(startupAssets.overrideNetwork(roundNumber)).thenReturn(Optional.empty());
        Optional<Network> result = startupAssets.overrideNetwork(roundNumber);
        assertThat(result).isNotPresent();
    }

    @Test
    void testArchiveInitialConditionsExecutesSuccessfully() {
        StartupAssets startupAssets = mock(StartupAssets.class);
        doNothing().when(startupAssets).archiveInitialConditions();
        assertThatCode(() -> startupAssets.archiveInitialConditions()).doesNotThrowAnyException();
        verify(startupAssets, times(1)).archiveInitialConditions();
    }
}
