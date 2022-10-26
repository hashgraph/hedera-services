/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services;

import static com.hedera.services.context.AppsManager.APPS;
import static com.swirlds.common.system.PlatformStatus.ACTIVE;
import static com.swirlds.common.system.PlatformStatus.FREEZE_COMPLETE;
import static com.swirlds.common.system.PlatformStatus.STARTING_UP;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.services.context.CurrentPlatformStatus;
import com.hedera.services.context.MutableStateChildren;
import com.hedera.services.context.NodeInfo;
import com.hedera.services.context.properties.SerializableSemVers;
import com.hedera.services.grpc.GrpcStarter;
import com.hedera.services.state.exports.AccountsExporter;
import com.hedera.services.state.migration.AccountStorageAdapter;
import com.hedera.services.state.validation.LedgerValidator;
import com.hedera.services.stats.ServicesStatsManager;
import com.hedera.services.stream.RecordStreamManager;
import com.hedera.services.utils.NamedDigestFactory;
import com.hedera.services.utils.SystemExits;
import com.swirlds.common.notification.NotificationEngine;
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
class ServicesMainTest {
    private final long selfId = 123L;
    private final long unselfId = 666L;
    private final NodeId nodeId = new NodeId(false, selfId);
    private final NodeId edonId = new NodeId(false, unselfId);

    @Mock private Platform platform;
    @Mock private SystemExits systemExits;
    @Mock private PrintStream consoleOut;
    @Mock private Supplier<Charset> nativeCharset;
    @Mock private ServicesApp app;
    @Mock private NamedDigestFactory namedDigestFactory;
    @Mock private MutableStateChildren workingState;
    @Mock private AccountStorageAdapter accounts;
    @Mock private LedgerValidator ledgerValidator;
    @Mock private NodeInfo nodeInfo;
    @Mock private ReconnectCompleteListener reconnectListener;
    @Mock private StateWriteToDiskCompleteListener stateToDiskListener;
    @Mock private IssListener issListener;
    @Mock private NewSignedStateListener newSignedStateListener;
    @Mock private NotificationEngine notificationEngine;
    @Mock private ServicesStatsManager statsManager;
    @Mock private AccountsExporter accountsExporter;
    @Mock private GrpcStarter grpcStarter;
    @Mock private CurrentPlatformStatus currentPlatformStatus;
    @Mock private RecordStreamManager recordStreamManager;

    private ServicesMain subject = new ServicesMain();

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
        withRunnableApp();

        // when:
        subject.init(platform, nodeId);

        // then:
        verify(ledgerValidator).validate(accounts);
        verify(nodeInfo).validateSelfAccountIfStaked();
        // and:
        verify(notificationEngine).register(IssListener.class, issListener);
        verify(notificationEngine).register(NewSignedStateListener.class, newSignedStateListener);
        verify(statsManager).initializeFor(platform);
        verify(accountsExporter).toFile(accounts);
        verify(notificationEngine).register(ReconnectCompleteListener.class, reconnectListener);
        verify(notificationEngine)
                .register(StateWriteToDiskCompleteListener.class, stateToDiskListener);
        verify(grpcStarter).startIfAppropriate();
    }

    @Test
    void noopsAsExpected() {
        // expect:
        Assertions.assertDoesNotThrow(subject::run);
        Assertions.assertDoesNotThrow(subject::preEvent);
    }

    @Test
    void createsNewState() {
        // expect:
        assertThat(subject.newState(), instanceOf(ServicesState.class));
    }

    @Test
    void updatesCurrentMiscPlatformStatus() throws NoSuchAlgorithmException {
        withRunnableApp();
        withChangeableApp();

        // given:
        subject.init(platform, nodeId);

        // when:
        subject.platformStatusChange(STARTING_UP);

        // then:
        verify(currentPlatformStatus).set(STARTING_UP);
    }

    @Test
    void updatesCurrentActivePlatformStatus() throws NoSuchAlgorithmException {
        withRunnableApp();
        withChangeableApp();

        given(app.recordStreamManager()).willReturn(recordStreamManager);
        // and:
        subject.init(platform, nodeId);

        // when:
        subject.platformStatusChange(ACTIVE);

        // then:
        verify(currentPlatformStatus).set(ACTIVE);
        verify(recordStreamManager).setInFreeze(false);
    }

    @Test
    void updatesCurrentMaintenancePlatformStatus() throws NoSuchAlgorithmException {
        withRunnableApp();
        withChangeableApp();

        given(app.recordStreamManager()).willReturn(recordStreamManager);
        // and:
        subject.init(platform, nodeId);

        // when:
        subject.platformStatusChange(FREEZE_COMPLETE);

        // then:
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

    private void withRunnableApp() throws NoSuchAlgorithmException {
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
        given(app.platformStatus()).willReturn(currentPlatformStatus);
        given(app.nodeId()).willReturn(nodeId);
    }
}
