/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.mockStatic;

import com.hedera.node.app.version.ServicesSoftwareVersion;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.io.filesystem.FileSystemManager;
import com.swirlds.common.io.utility.RecycleBin;
import com.swirlds.common.merkle.crypto.MerkleCryptography;
import com.swirlds.common.metrics.platform.DefaultMetricsProvider;
import com.swirlds.common.platform.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.platform.builder.PlatformBuilder;
import com.swirlds.platform.config.legacy.ConfigurationException;
import com.swirlds.platform.config.legacy.LegacyConfigProperties;
import com.swirlds.platform.config.legacy.LegacyConfigPropertiesLoader;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.SystemExitUtils;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.util.BootstrapUtils;
import com.swirlds.state.merkle.MerkleStateRoot;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class ServicesMainTest {
    private static final MockedStatic<LegacyConfigPropertiesLoader> legacyConfigPropertiesLoaderMockedStatic =
            mockStatic(LegacyConfigPropertiesLoader.class);
    private static final MockedStatic<BootstrapUtils> bootstrapUtilsMockedStatic = mockStatic(BootstrapUtils.class);

    @Mock(strictness = LENIENT)
    private LegacyConfigProperties legacyConfigProperties;

    @Mock(strictness = LENIENT)
    private AddressBook addressBook;

    @Mock(strictness = LENIENT)
    private DefaultMetricsProvider metricsProvider;

    @Mock(strictness = LENIENT)
    private Metrics metrics;

    @Mock(strictness = LENIENT)
    private FileSystemManager fileSystemManager;

    @Mock(strictness = LENIENT)
    private RecycleBin recycleBin;

    @Mock(strictness = LENIENT)
    private MerkleCryptography merkleCryptography;

    @Mock(strictness = LENIENT)
    BiFunction<Configuration, Cryptography, MerkleCryptography> merkleCryptographyFn;

    @Mock(strictness = LENIENT)
    private PlatformContext platformContext;

    @Mock(strictness = LENIENT)
    private PlatformBuilder platformBuilder;

    @Mock(strictness = LENIENT)
    private ReservedSignedState reservedSignedState;

    @Mock(strictness = LENIENT)
    private SignedState signedState;

    @Mock(strictness = LENIENT)
    private Platform platform;

    @Mock(strictness = LENIENT)
    private Hedera hedera;

    private final ServicesMain subject = new ServicesMain();

    // no local nodes specified but more than one match in address book
    @Test
    void hardExitOnTooManyLocalNodes() {
        withBadCommandLineArgs();
        String[] args = {};

        try (MockedStatic<SystemExitUtils> systemExitUtilsMockedStatic = mockStatic(SystemExitUtils.class)) {
            assertThatThrownBy(() -> ServicesMain.main(args)).isInstanceOf(ConfigurationException.class);

            systemExitUtilsMockedStatic.verify(() -> SystemExitUtils.exitSystem(NODE_ADDRESS_MISMATCH));
        }
    }

    // local node specified which does not match the address book
    @Test
    void hardExitOnNonMatchingNodeId() {
        withBadCommandLineArgs();
        String[] args = {"-local", "1234"}; // 1234 does not match anything in address book

        try (MockedStatic<SystemExitUtils> systemExitUtilsMockedStatic = mockStatic(SystemExitUtils.class)) {
            assertThatThrownBy(() -> ServicesMain.main(args)).isInstanceOf(ConfigurationException.class);

            systemExitUtilsMockedStatic.verify(() -> SystemExitUtils.exitSystem(NODE_ADDRESS_MISMATCH));
        }
    }

    // more than one local node specified which matches the address book
    @Test
    void hardExitOnTooManyMatchingNodes() {
        withBadCommandLineArgs();
        String[] args = {"-local", "1", "2"}; // both "1" and "2" match entries in address book

        try (MockedStatic<SystemExitUtils> systemExitUtilsMockedStatic = mockStatic(SystemExitUtils.class)) {
            systemExitUtilsMockedStatic
                    .when(() -> SystemExitUtils.exitSystem(any()))
                    .thenThrow(new UnsupportedOperationException());
            assertThatThrownBy(() -> ServicesMain.main(args)).isInstanceOf(UnsupportedOperationException.class);

            systemExitUtilsMockedStatic.verify(() -> SystemExitUtils.exitSystem(NODE_ADDRESS_MISMATCH));
        }
    }

    @Test
    void returnsSerializableVersion() {
        assertInstanceOf(ServicesSoftwareVersion.class, subject.getSoftwareVersion());
    }

    @Test
    void noopsAsExpected() {
        // expect:
        assertDoesNotThrow(subject::run);
    }

    @Test
    void createsNewMerkleStateRoot() {
        // expect:
        assertThat(subject.newMerkleStateRoot(), instanceOf(MerkleStateRoot.class));
    }

    private void withBadCommandLineArgs() {
        legacyConfigPropertiesLoaderMockedStatic
                .when(() -> LegacyConfigPropertiesLoader.loadConfigFile(any()))
                .thenReturn(legacyConfigProperties);

        List<NodeId> nodeIds = new ArrayList<>();
        nodeIds.add(NodeId.of(1));
        nodeIds.add(NodeId.of(2));

        bootstrapUtilsMockedStatic
                .when(() -> BootstrapUtils.getNodesToRun(any(), any()))
                .thenReturn(nodeIds);
    }
}
