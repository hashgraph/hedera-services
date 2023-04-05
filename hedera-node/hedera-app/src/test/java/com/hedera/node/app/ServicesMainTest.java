/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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
import static com.swirlds.common.system.PlatformStatus.ACTIVE;
import static com.swirlds.common.system.PlatformStatus.FREEZE_COMPLETE;
import static com.swirlds.common.system.PlatformStatus.STARTING_UP;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.node.app.service.mono.ServicesApp;
import com.hedera.node.app.service.mono.ServicesState;
import com.hedera.node.app.service.mono.context.CurrentPlatformStatus;
import com.hedera.node.app.service.mono.context.MutableStateChildren;
import com.hedera.node.app.service.mono.context.NodeInfo;
import com.hedera.node.app.service.mono.context.properties.SerializableSemVers;
import com.hedera.node.app.service.mono.grpc.GrpcStarter;
import com.hedera.node.app.service.mono.state.exports.AccountsExporter;
import com.hedera.node.app.service.mono.state.logic.StatusChangeListener;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.state.validation.LedgerValidator;
import com.hedera.node.app.service.mono.stats.ServicesStatsManager;
import com.hedera.node.app.service.mono.stream.RecordStreamManager;
import com.hedera.node.app.service.mono.utils.NamedDigestFactory;
import com.hedera.node.app.service.mono.utils.SystemExits;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.notification.listeners.PlatformStatusChangeListener;
import com.swirlds.common.notification.listeners.PlatformStatusChangeNotification;
import com.swirlds.common.notification.listeners.ReconnectCompleteListener;
import com.swirlds.common.notification.listeners.StateWriteToDiskCompleteListener;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.state.notifications.IssListener;
import com.swirlds.common.system.state.notifications.NewSignedStateListener;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class ServicesMainTest {
    private final long selfId = 123L;
    private final long unselfId = 666L;
    private final NodeId nodeId = new NodeId(false, selfId);
    private final NodeId edonId = new NodeId(false, unselfId);

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

    private final ServicesMain subject = new ServicesMain();

    @Test
    void throwsErrorOnMissingApp() {
        // expect:
        Assertions.assertThrows(AssertionError.class, () -> subject.init(platform, edonId));
    }

    @Test
    void returnsSerializableVersion() {
        assertInstanceOf(SerializableSemVers.class, subject.getSoftwareVersion());
    }

    @Test
    void failsOnWrongNativeCharset() {
        withDoomedApp();

        given(nativeCharset.get()).willReturn(StandardCharsets.US_ASCII);

        // when:
        subject.init(platform, nodeId);

        // then:
        verify(systemExits).fail(1);
    }

    @Test
    void failsOnUnavailableDigest() throws NoSuchAlgorithmException {
        withDoomedApp();

        given(nativeCharset.get()).willReturn(UTF_8);
        given(namedDigestFactory.forName("SHA-384")).willThrow(NoSuchAlgorithmException.class);
        given(app.digestFactory()).willReturn(namedDigestFactory);

        // when:
        subject.init(platform, nodeId);

        // then:
        verify(systemExits).fail(1);
    }

    @Test
    void doesAppDrivenInit() throws NoSuchAlgorithmException {
        withRunnableApp(app);
        withChangeableApp();

        // when:
        subject.init(platform, nodeId);

        // then:
        verify(ledgerValidator).validate(accounts);
        verify(nodeInfo).validateSelfAccountIfStaked();
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
    void createsNewState() {
        // expect:
        assertThat(subject.newState(), instanceOf(ServicesState.class));
    }

    @Test
    void updatesCurrentMiscPlatformStatus() throws NoSuchAlgorithmException {
        final var listener = new StatusChangeListener(currentPlatformStatus, nodeId, recordStreamManager);
        withRunnableApp(app);
        withChangeableApp();
        withNotificationEngine();

        subject.init(platform, nodeId);
        listener.notify(new PlatformStatusChangeNotification(STARTING_UP));

        verify(currentPlatformStatus).set(STARTING_UP);
    }

    @Test
    void updatesCurrentActivePlatformStatus() throws NoSuchAlgorithmException {
        final var listener = new StatusChangeListener(currentPlatformStatus, nodeId, recordStreamManager);
        withRunnableApp(app);
        withChangeableApp();
        withNotificationEngine();

        subject.init(platform, nodeId);
        listener.notify(new PlatformStatusChangeNotification(ACTIVE));

        verify(currentPlatformStatus).set(ACTIVE);
        verify(recordStreamManager).setInFreeze(false);
    }

    @Test
    void updatesCurrentMaintenancePlatformStatus() throws NoSuchAlgorithmException {
        final var listener = new StatusChangeListener(currentPlatformStatus, nodeId, recordStreamManager);
        withRunnableApp(app);
        withChangeableApp();
        withNotificationEngine();

        subject.init(platform, nodeId);
        listener.notify(new PlatformStatusChangeNotification(FREEZE_COMPLETE));

        verify(currentPlatformStatus).set(FREEZE_COMPLETE);
        verify(recordStreamManager).setInFreeze(true);
    }

    @Test
    void failsHardIfCannotInit() throws NoSuchAlgorithmException {
        withFailingApp();

        // when:
        subject.init(platform, nodeId);

        // then:
        verify(systemExits).fail(1);
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
        given(app.nodeId()).willReturn(nodeId);
    }

    private void withNotificationEngine() {
        given(notificationEngine.register(any(), any())).willReturn(true);
    }
}
