/*
 * Copyright (C) 2021-2025 Hedera Hashgraph, LLC
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

import static com.swirlds.platform.system.SystemExitCode.NODE_ADDRESS_MISMATCH;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.node.app.version.ServicesSoftwareVersion;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.config.legacy.ConfigurationException;
import com.swirlds.platform.config.legacy.LegacyConfigProperties;
import com.swirlds.platform.config.legacy.LegacyConfigPropertiesLoader;
import com.swirlds.platform.system.SystemExitUtils;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.state.merkle.MerkleStateRoot;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class ServicesMainTest {
    private static final MockedStatic<LegacyConfigPropertiesLoader> legacyConfigPropertiesLoaderMockedStatic =
            mockStatic(LegacyConfigPropertiesLoader.class);

    @Mock(strictness = LENIENT)
    private LegacyConfigProperties legacyConfigProperties = mock(LegacyConfigProperties.class);

    @Mock(strictness = LENIENT)
    private Metrics metrics;

    @Mock(strictness = LENIENT)
    private Hedera hedera;

    @Mock
    private MerkleStateRoot state;

    private final ServicesMain subject = new ServicesMain();

    @AfterAll
    static void afterAll() {
        legacyConfigPropertiesLoaderMockedStatic.close();
    }

    // no local nodes specified, no environment nodes specified
    @Test
    void throwsExceptionOnNoNodesToRun() {
        withBadCommandLineArgs();
        String[] args = {};
        assertThatThrownBy(() -> ServicesMain.main(args)).isInstanceOf(IllegalStateException.class);
    }

    // more than one local node specified on the commandline
    @Test
    void hardExitOnTooManyCliNodes() {
        withBadCommandLineArgs();
        String[] args = {"-local", "1", "2"}; // both "1" and "2" match entries in address book

        try (MockedStatic<SystemExitUtils> systemExitUtilsMockedStatic = mockStatic(SystemExitUtils.class)) {
            assertThatThrownBy(() -> ServicesMain.main(args)).isInstanceOf(ConfigurationException.class);
            systemExitUtilsMockedStatic.verify(() -> SystemExitUtils.exitSystem(NODE_ADDRESS_MISMATCH));
        }
    }

    @Test
    void delegatesSoftwareVersion() {
        ServicesMain.initGlobal(hedera, metrics);
        final var mockVersion = new ServicesSoftwareVersion(SemanticVersion.DEFAULT);
        given(hedera.getSoftwareVersion()).willReturn(mockVersion);
        assertSame(mockVersion, subject.getSoftwareVersion());
    }

    @Test
    void noopsAsExpected() {
        ServicesMain.initGlobal(hedera, metrics);
        assertDoesNotThrow(subject::run);
    }

    @Test
    void createsNewStateRoot() {
        ServicesMain.initGlobal(hedera, metrics);
        given(hedera.newStateRoot()).willReturn(state);
        assertSame(state, subject.newStateRoot());
    }

    private void withBadCommandLineArgs() {
        legacyConfigPropertiesLoaderMockedStatic
                .when(() -> LegacyConfigPropertiesLoader.loadConfigFile(any()))
                .thenReturn(legacyConfigProperties);

        when(legacyConfigProperties.getAddressBook()).thenReturn(new AddressBook());
    }
}
