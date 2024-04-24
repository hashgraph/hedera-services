/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.reconnect.emergency;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.merkle.synchronization.config.ReconnectConfig;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.AssertionUtils;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.common.test.fixtures.merkle.util.PairedStreams;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.common.threading.pool.CachedPoolParallelExecutor;
import com.swirlds.common.threading.pool.ParallelExecutionException;
import com.swirlds.common.threading.pool.ParallelExecutor;
import com.swirlds.common.utility.Clearable;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.platform.config.StateConfig;
import com.swirlds.platform.metrics.ReconnectMetrics;
import com.swirlds.platform.network.Connection;
import com.swirlds.platform.network.NetworkProtocolException;
import com.swirlds.platform.network.protocol.EmergencyReconnectProtocolFactory;
import com.swirlds.platform.network.protocol.Protocol;
import com.swirlds.platform.network.protocol.ProtocolFactory;
import com.swirlds.platform.reconnect.DummyConnection;
import com.swirlds.platform.reconnect.ReconnectController;
import com.swirlds.platform.reconnect.ReconnectHelper;
import com.swirlds.platform.reconnect.ReconnectLearnerFactory;
import com.swirlds.platform.reconnect.ReconnectLearnerThrottle;
import com.swirlds.platform.reconnect.ReconnectThrottle;
import com.swirlds.platform.recovery.EmergencyRecoveryManager;
import com.swirlds.platform.recovery.emergencyfile.EmergencyRecoveryFile;
import com.swirlds.platform.state.RandomSignedStateGenerator;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.status.StatusActionSubmitter;
import com.swirlds.platform.test.fixtures.addressbook.RandomAddressBookGenerator;
import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests the emergency reconnect protocol learner and teacher flows.
 */
class EmergencyReconnectTests {
    private static final Future<Boolean> trueFuture = mock(Future.class);
    private final RandomSignedStateGenerator signedStateGenerator = new RandomSignedStateGenerator();
    private final NodeId learnerId = new NodeId(0L);
    private final NodeId teacherId = new NodeId(1L);
    private final ReconnectThrottle reconnectThrottle = mock(ReconnectThrottle.class);
    private final Supplier<ReservedSignedState> emergencyState = mock(Supplier.class);
    private final ParallelExecutor executor = new CachedPoolParallelExecutor(getStaticThreadManager(), "test-executor");
    private Protocol learnerProtocol;
    private Protocol teacherProtocol;
    private final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
    private final PlatformContext platformContext =
            TestPlatformContextBuilder.create().withConfiguration(configuration).build();

    @BeforeEach
    public void setup() throws ExecutionException, InterruptedException, ConstructableRegistryException {
        ConstructableRegistry.getInstance().registerConstructables("");

        when(trueFuture.get()).thenReturn(true);
        when(reconnectThrottle.initiateReconnect(any())).thenReturn(true);

        if (executor.isMutable()) {
            executor.start();
        }
        when(emergencyState.get()).thenReturn(null);
    }

    @DisplayName("Verify learner-teacher interaction when teacher does not has a compatible state")
    @Test
    void teacherDoesNotHaveCompatibleState() throws InterruptedException {
        final Random random = RandomUtils.getRandomPrintSeed();
        final Hash stateHash = RandomUtils.randomHash(random);
        final NotificationEngine notificationEngine = NotificationEngine.buildEngine(getStaticThreadManager());
        final EmergencyRecoveryFile emergencyRecoveryFile =
                new EmergencyRecoveryFile(1L, stateHash, RandomUtils.randomInstant(random));

        final ReconnectController reconnectController = mock(ReconnectController.class);
        when(reconnectController.acquireLearnerPermit()).thenReturn(true);

        learnerProtocol = createLearnerProtocol(notificationEngine, emergencyRecoveryFile, reconnectController);
        teacherProtocol = createTeacherProtocol(notificationEngine, reconnectController);

        mockTeacherDoesNotHaveCompatibleState();

        executeReconnect();

        assertTeacherSearchedForState(emergencyRecoveryFile);
        assertLearnerDoesNotReconnect(reconnectController);
        notificationEngine.shutdown();
    }

    @DisplayName("Verify learner-teacher interaction when teacher has compatible state")
    @Test
    void teacherHasCompatibleState() throws InterruptedException {
        final Random random = RandomUtils.initRandom(null);
        final NotificationEngine notificationEngine = NotificationEngine.buildEngine(getStaticThreadManager());
        final int numNodes = 4;
        final List<NodeId> nodeIds =
                IntStream.range(0, numNodes).mapToObj(NodeId::new).toList();
        final long emergencyRound = 1L;

        final AddressBook addressBook = newAddressBook(random, numNodes);

        // Building the signed state takes a reservation
        final SignedState teacherState = signedStateGenerator
                .setRound(emergencyRound)
                .setAddressBook(addressBook)
                .setSigningNodeIds(nodeIds)
                .build();

        MerkleCryptoFactory.getInstance().digestSync(teacherState.getState());

        final Hash emergencyStateHash = teacherState.getState().getHash();

        final SignedState learnerState = signedStateGenerator
                .setRound(emergencyRound - 10)
                .setAddressBook(addressBook)
                .build();
        learnerState.getState().setHash(RandomUtils.randomHash(random));

        final AtomicReference<ReservedSignedState> receivedSignedState = new AtomicReference<>();
        final ReconnectController reconnectController = createReconnectController(
                addressBook, learnerState::getState, s -> receivedSignedState.set(s.reserve("test")));
        reconnectController.start();

        // Give the reconnect controller some time to start waiting for the connection before the learner
        // attempts to acquire the provide permit
        TimeUnit.MILLISECONDS.sleep(100);

        final EmergencyRecoveryFile emergencyRecoveryFile =
                new EmergencyRecoveryFile(emergencyRound, emergencyStateHash, RandomUtils.randomInstant(random));

        learnerProtocol = createLearnerProtocol(notificationEngine, emergencyRecoveryFile, reconnectController);
        teacherProtocol = createTeacherProtocol(notificationEngine, reconnectController);

        mockTeacherHasCompatibleState(teacherState);

        AssertionUtils.completeBeforeTimeout(
                this::executeReconnect, Duration.ofSeconds(5), "Reconnect should have completed or failed");

        checkSignedStateReservations(receivedSignedState.get().get(), teacherState);
        assertTeacherSearchedForState(emergencyRecoveryFile);
        assertLearnerReceivedTeacherState(teacherState, receivedSignedState);
        notificationEngine.shutdown();

        receivedSignedState.get().close();
    }

    private void checkSignedStateReservations(final SignedState receivedSignedState, final SignedState teacherState) {
        // The learner's state has an explicit reservation of 1
        assertEquals(
                1,
                receivedSignedState.getReservationCount(),
                "incorrect number of reservations on the learner's received state");

        // The teacher state is created by the generator with one reservation.
        // The teacher state has released all reservations by now.
        assertEquals(-1, teacherState.getReservationCount(), "incorrect number of reservations on the teacher state");
    }

    private void assertLearnerDoesNotReconnect(final ReconnectController reconnectController)
            throws InterruptedException {
        verify(reconnectController, times(0).description("Connection should be provided"))
                .provideLearnerConnection(any(Connection.class));
        verify(
                        reconnectController,
                        times(1).description("Permit should be canceled if the reconnect does not complete"))
                .cancelLearnerPermit();
    }

    private void assertLearnerReceivedTeacherState(
            final SignedState teacherState, final AtomicReference<ReservedSignedState> receivedSignedState) {
        assertEquals(
                teacherState.getState().getHash(),
                receivedSignedState.get().get().getState().getHash(),
                "Learner did not receive the teacher's state");
    }

    private void assertTeacherSearchedForState(final EmergencyRecoveryFile emergencyRecoveryFile) {
        verify(emergencyState, times(1).description("Teacher did not search for the correct state"))
                .get();
    }

    private ReconnectController createReconnectController(
            final AddressBook addressBook,
            final Supplier<State> learnerState,
            final Consumer<SignedState> receivedStateConsumer) {

        final StateConfig stateConfig = configuration.getConfigData(StateConfig.class);
        final ReconnectHelper helper = new ReconnectHelper(
                () -> {},
                mock(Clearable.class),
                learnerState,
                () -> -1L,
                mock(ReconnectLearnerThrottle.class),
                receivedStateConsumer,
                new ReconnectLearnerFactory(
                        TestPlatformContextBuilder.create().build(),
                        getStaticThreadManager(),
                        addressBook,
                        Duration.of(100_000, ChronoUnit.MILLIS),
                        mock(ReconnectMetrics.class)),
                stateConfig);

        final Configuration configuration = new TestConfigBuilder().getOrCreateConfig();
        final ReconnectConfig reconnectConfig = configuration.getConfigData(ReconnectConfig.class);

        return new ReconnectController(reconnectConfig, getStaticThreadManager(), helper, () -> {});
    }

    private void executeReconnect() {
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        try (final PairedStreams pairedStreams = new PairedStreams()) {
            executor.doParallel(
                    doTeacher(
                            teacherProtocol,
                            new DummyConnection(
                                    platformContext,
                                    teacherId,
                                    learnerId,
                                    pairedStreams.getTeacherInput(),
                                    pairedStreams.getTeacherOutput())),
                    doLearner(
                            learnerProtocol,
                            new DummyConnection(
                                    platformContext,
                                    learnerId,
                                    teacherId,
                                    pairedStreams.getLearnerInput(),
                                    pairedStreams.getLearnerOutput())));

        } catch (final IOException | ParallelExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private Protocol createTeacherProtocol(
            final NotificationEngine notificationEngine, final ReconnectController reconnectController) {
        final ProtocolFactory emergencyReconnectProtocolFactory = new EmergencyReconnectProtocolFactory(
                platformContext,
                getStaticThreadManager(),
                notificationEngine,
                mock(EmergencyRecoveryManager.class),
                reconnectThrottle,
                emergencyState,
                Duration.of(100, ChronoUnit.MILLIS),
                mock(ReconnectMetrics.class),
                reconnectController,
                mock(StatusActionSubmitter.class),
                configuration);
        return emergencyReconnectProtocolFactory.build(teacherId);
    }

    private EmergencyReconnectProtocol createLearnerProtocol(
            final NotificationEngine notificationEngine,
            final EmergencyRecoveryFile emergencyRecoveryFile,
            final ReconnectController reconnectController) {
        final EmergencyRecoveryManager emergencyRecoveryManager = mock(EmergencyRecoveryManager.class);
        when(emergencyRecoveryManager.isEmergencyStateRequired()).thenReturn(true);
        when(emergencyRecoveryManager.getEmergencyRecoveryFile()).thenReturn(emergencyRecoveryFile);
        return new EmergencyReconnectProtocolFactory(
                        platformContext,
                        getStaticThreadManager(),
                        notificationEngine,
                        emergencyRecoveryManager,
                        mock(ReconnectThrottle.class),
                        emergencyState,
                        Duration.of(100, ChronoUnit.MILLIS),
                        mock(ReconnectMetrics.class),
                        reconnectController,
                        mock(StatusActionSubmitter.class),
                        configuration)
                .build(learnerId);
    }

    private void mockTeacherHasCompatibleState(final SignedState teacherState) {
        when(emergencyState.get()).thenAnswer(i -> teacherState.reserve("test"));
    }

    private AddressBook newAddressBook(final Random random, final int numNodes) {
        return new RandomAddressBookGenerator(random)
                .setSize(numNodes)
                .setAverageWeight(100L)
                .setWeightDistributionStrategy(RandomAddressBookGenerator.WeightDistributionStrategy.BALANCED)
                .setHashStrategy(RandomAddressBookGenerator.HashStrategy.REAL_HASH)
                .build();
    }

    private void mockTeacherDoesNotHaveCompatibleState() {
        when(emergencyState.get()).thenReturn(null);
    }

    private Callable<Void> doLearner(final Protocol learnerProtocol, final Connection connection) {
        return () -> {
            if (!learnerProtocol.shouldInitiate()) {
                throw new RuntimeException("Learner should initiate emergency reconnect protocol");
            }
            try {
                learnerProtocol.runProtocol(connection);
            } catch (final NetworkProtocolException | IOException | InterruptedException e) {
                throw new ParallelExecutionException(e);
            }
            return null;
        };
    }

    private Callable<Boolean> doTeacher(final Protocol teacherProtocol, final Connection connection) {
        return () -> {
            if (!teacherProtocol.shouldAccept()) {
                throw new RuntimeException("Teacher should accept emergency reconnect protocol initiation");
            }
            try {
                teacherProtocol.runProtocol(connection);
            } catch (final NetworkProtocolException | IOException | InterruptedException e) {
                throw new ParallelExecutionException(e);
            }
            return true;
        };
    }
}
