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

import static com.hedera.node.app.service.mono.context.AppsManager.APPS;
import static com.swirlds.platform.system.SystemExitCode.NODE_ADDRESS_MISMATCH;
import static com.swirlds.platform.system.status.PlatformStatus.ACTIVE;
import static com.swirlds.platform.system.status.PlatformStatus.FREEZE_COMPLETE;
import static com.swirlds.platform.system.status.PlatformStatus.STARTING_UP;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.mono.ServicesApp;
import com.hedera.node.app.service.mono.context.CurrentPlatformStatus;
import com.hedera.node.app.service.mono.context.MutableStateChildren;
import com.hedera.node.app.service.mono.context.NodeInfo;
import com.hedera.node.app.service.mono.grpc.GrpcStarter;
import com.hedera.node.app.service.mono.state.exports.AccountsExporter;
import com.hedera.node.app.service.mono.state.logic.StatusChangeListener;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.state.validation.LedgerValidator;
import com.hedera.node.app.service.mono.stats.ServicesStatsManager;
import com.hedera.node.app.service.mono.stream.RecordStreamManager;
import com.hedera.node.app.service.mono.utils.NamedDigestFactory;
import com.hedera.node.app.service.mono.utils.SystemExits;
import com.hedera.node.app.version.HederaSoftwareVersion;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.config.legacy.ConfigurationException;
import com.swirlds.platform.config.legacy.LegacyConfigProperties;
import com.swirlds.platform.config.legacy.LegacyConfigPropertiesLoader;
import com.swirlds.platform.listeners.PlatformStatusChangeListener;
import com.swirlds.platform.listeners.PlatformStatusChangeNotification;
import com.swirlds.platform.listeners.ReconnectCompleteListener;
import com.swirlds.platform.listeners.StateWriteToDiskCompleteListener;
import com.swirlds.platform.state.State;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.SystemExitUtils;
import com.swirlds.platform.system.state.notifications.IssListener;
import com.swirlds.platform.system.state.notifications.NewSignedStateListener;
import com.swirlds.platform.util.BootstrapUtils;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
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

    private final NodeId selfId = new NodeId(123L);
    private final NodeId unselfId = new NodeId(666L);

    @Mock
    private Platform platform;

    @Mock
    private SystemExits systemExits;

    @Mock
    private PrintStream consoleOut;

    @Mock
    private Supplier<Charset> nativeCharset;

    @Mock
    private ServicesApp app;

    @Mock
    private NamedDigestFactory namedDigestFactory;

    @Mock
    private MutableStateChildren workingState;

    @Mock
    private AccountStorageAdapter accounts;

    @Mock
    private LedgerValidator ledgerValidator;

    @Mock
    private NodeInfo nodeInfo;

    @Mock
    private ReconnectCompleteListener reconnectListener;

    @Mock
    private StateWriteToDiskCompleteListener stateToDiskListener;

    @Mock
    private PlatformStatusChangeListener statusChangeListener;

    @Mock
    private IssListener issListener;

    @Mock
    private NewSignedStateListener newSignedStateListener;

    @Mock
    private NotificationEngine notificationEngine;

    @Mock
    private ServicesStatsManager statsManager;

    @Mock
    private AccountsExporter accountsExporter;

    @Mock
    private GrpcStarter grpcStarter;

    @Mock
    private CurrentPlatformStatus currentPlatformStatus;

    @Mock
    private RecordStreamManager recordStreamManager;

    @Mock
    private LegacyConfigProperties legacyConfigProperties;

    private final ServicesMain subject = new ServicesMain();

    @Test
    @Disabled("Mono-specific behavior")
    void throwsErrorOnMissingApp() {
        // expect:
        Assertions.assertThrows(AssertionError.class, () -> subject.init(platform, unselfId));
    }

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
        assertInstanceOf(HederaSoftwareVersion.class, subject.getSoftwareVersion());
    }

    @Test
    @Disabled("Mono-specific behavior")
    void failsOnWrongNativeCharset() {
        withDoomedApp();

        given(nativeCharset.get()).willReturn(StandardCharsets.US_ASCII);

        // when:
        subject.init(platform, selfId);

        // then:
        verify(systemExits).fail(1);
    }

    @Test
    @Disabled("Mono-specific behavior")
    void failsOnUnavailableDigest() throws NoSuchAlgorithmException {
        withDoomedApp();

        given(nativeCharset.get()).willReturn(UTF_8);
        given(namedDigestFactory.forName("SHA-384")).willThrow(NoSuchAlgorithmException.class);
        given(app.digestFactory()).willReturn(namedDigestFactory);

        // when:
        subject.init(platform, selfId);

        // then:
        verify(systemExits).fail(1);
    }

    @Test
    @Disabled("Mono-specific behavior")
    void doesAppDrivenInit() throws NoSuchAlgorithmException {
        withRunnableApp(app);
        withChangeableApp();

        // when:
        subject.init(platform, selfId);

        // then:
        verify(ledgerValidator).validate(accounts);
        verify(nodeInfo).validateSelfAccountIfNonZeroStake();
        // and:
        verify(notificationEngine).register(PlatformStatusChangeListener.class, statusChangeListener);
        verify(notificationEngine).register(IssListener.class, issListener);
        verify(notificationEngine).register(NewSignedStateListener.class, newSignedStateListener);
        verify(statsManager).initializeFor(platform);
        verify(accountsExporter).toFile(accounts);
        verify(notificationEngine).register(ReconnectCompleteListener.class, reconnectListener);
        verify(notificationEngine).register(StateWriteToDiskCompleteListener.class, stateToDiskListener);
        verify(grpcStarter).startIfAppropriate();
    }

    @Test
    void noopsAsExpected() {
        // expect:
        Assertions.assertDoesNotThrow(subject::run);
    }

    @Test
    void createsNewMerkleStateRoot() {
        // expect:
        assertThat(subject.newMerkleStateRoot(), instanceOf(State.class));
        // FUTURE WORK: https://github.com/hashgraph/hedera-services/issues/11773
        // assertThat(subject.newMerkleStateRoot(), instanceOf(MerkleHederaState.class));
    }

    @Test
    @Disabled("Mono-specific behavior")
    void updatesCurrentMiscPlatformStatus() throws NoSuchAlgorithmException {
        final var listener = new StatusChangeListener(currentPlatformStatus, selfId, recordStreamManager);
        withRunnableApp(app);
        withChangeableApp();
        withNotificationEngine();

        subject.init(platform, selfId);
        listener.notify(new PlatformStatusChangeNotification(STARTING_UP));

        verify(currentPlatformStatus).set(STARTING_UP);
    }

    @Test
    @Disabled("Mono-specific behavior")
    void updatesCurrentActivePlatformStatus() throws NoSuchAlgorithmException {
        final var listener = new StatusChangeListener(currentPlatformStatus, selfId, recordStreamManager);
        withRunnableApp(app);
        withChangeableApp();
        withNotificationEngine();

        subject.init(platform, selfId);
        listener.notify(new PlatformStatusChangeNotification(ACTIVE));

        verify(currentPlatformStatus).set(ACTIVE);
        verify(recordStreamManager).setInFreeze(false);
    }

    @Test
    @Disabled("Mono-specific behavior")
    void updatesCurrentMaintenancePlatformStatus() throws NoSuchAlgorithmException {
        final var listener = new StatusChangeListener(currentPlatformStatus, selfId, recordStreamManager);
        withRunnableApp(app);
        withChangeableApp();
        withNotificationEngine();

        subject.init(platform, selfId);
        listener.notify(new PlatformStatusChangeNotification(FREEZE_COMPLETE));

        verify(currentPlatformStatus).set(FREEZE_COMPLETE);
        verify(recordStreamManager).setInFreeze(true);
    }

    @Test
    @Disabled("Mono-specific behavior")
    void failsHardIfCannotInit() throws NoSuchAlgorithmException {
        withFailingApp();

        // when:
        subject.init(platform, selfId);

        // then:
        verify(systemExits).fail(1);
    }

    private void withBadCommandLineArgs() {
        legacyConfigPropertiesLoaderMockedStatic
                .when(() -> LegacyConfigPropertiesLoader.loadConfigFile(any()))
                .thenReturn(legacyConfigProperties);

        List<NodeId> nodeIds = new ArrayList<>();
        nodeIds.add(new NodeId(1));
        nodeIds.add(new NodeId(2));

        bootstrapUtilsMockedStatic
                .when(() -> BootstrapUtils.getNodesToRun(any(), any()))
                .thenReturn(nodeIds);
    }

    private void withDoomedApp() {
        APPS.save(selfId, app);
        given(app.nativeCharset()).willReturn(nativeCharset);
        given(app.systemExits()).willReturn(systemExits);
    }

    private void withFailingApp() throws NoSuchAlgorithmException {
        APPS.save(selfId, app);
        given(nativeCharset.get()).willReturn(UTF_8);
        given(namedDigestFactory.forName("SHA-384")).willReturn(null);
        given(app.nativeCharset()).willReturn(nativeCharset);
        given(app.digestFactory()).willReturn(namedDigestFactory);
        given(app.systemExits()).willReturn(systemExits);
    }

    private void withRunnableApp(final ServicesApp app) throws NoSuchAlgorithmException {
        APPS.save(selfId, app);
        given(nativeCharset.get()).willReturn(UTF_8);
        given(namedDigestFactory.forName("SHA-384")).willReturn(null);
        given(app.nativeCharset()).willReturn(nativeCharset);
        given(app.digestFactory()).willReturn(namedDigestFactory);
        given(app.consoleOut()).willReturn(Optional.of(consoleOut));
        given(app.workingState()).willReturn(workingState);
        given(workingState.accounts()).willReturn(accounts);
        given(app.ledgerValidator()).willReturn(ledgerValidator);
        given(app.nodeInfo()).willReturn(nodeInfo);
        given(app.platform()).willReturn(platform);
        given(app.statusChangeListener()).willReturn(statusChangeListener);
        given(app.issListener()).willReturn(issListener);
        given(app.newSignedStateListener()).willReturn(newSignedStateListener);
        given(app.notificationEngine()).willReturn(() -> notificationEngine);
        given(app.reconnectListener()).willReturn(reconnectListener);
        given(app.stateWriteToDiskListener()).willReturn(stateToDiskListener);
        given(app.statsManager()).willReturn(statsManager);
        given(app.accountsExporter()).willReturn(accountsExporter);
        given(app.grpcStarter()).willReturn(grpcStarter);
    }

    private void withChangeableApp() {
        given(app.nodeId()).willReturn(selfId);
    }

    private void withNotificationEngine() {
        given(notificationEngine.register(any(), any())).willReturn(true);
    }
}
