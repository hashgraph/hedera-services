// SPDX-License-Identifier: Apache-2.0
package org.hiero.consensus.reconnect.impl;

import static org.hiero.base.concurrent.manager.AdHocThreadManager.getStaticThreadManager;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.pbj.runtime.io.stream.WritableStreamingData;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.extensions.test.fixtures.TestConfigBuilder;
import com.swirlds.state.StateLifecycleManager;
import com.swirlds.state.merkle.VirtualMapState;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualMapLearner;
import com.swirlds.virtualmap.sync.LearningSynchronizer;
import com.swirlds.virtualmap.test.fixtures.sync.PairedStreams;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.hiero.consensus.state.signed.SigSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests how {@link ReconnectStateLearner} creates the learner, which copies the learner's data source, while it waits
 * for the teacher's signatures, and how it closes the learner if the reconnect fails before the synchronization.
 */
class ReconnectStateLearnerTest {

    private static final Duration RECONNECT_SOCKET_TIMEOUT = Duration.ofSeconds(5);

    /** How long a test waits for something that is expected to happen. */
    private static final long TIMEOUT_MILLIS = 5_000;

    // The teacher's signatures are written with the same connection type, which is incompatible with gzip
    private final Configuration configuration =
            new TestConfigBuilder().withValue("socket.gzipCompression", false).getOrCreateConfig();

    private PairedStreams pairedStreams;
    private DummyConnection teacherConnection;
    private LearningSynchronizer synchronizer;
    private VirtualMapLearner learner;
    private ReconnectStateLearner reconnectLearner;

    @BeforeEach
    void setUp() throws IOException {
        pairedStreams = new PairedStreams();
        teacherConnection =
                new DummyConnection(configuration, pairedStreams.getTeacherInput(), pairedStreams.getTeacherOutput());
        synchronizer = mock(LearningSynchronizer.class);
        learner = mock(VirtualMapLearner.class);

        final VirtualMapState currentState = mock(VirtualMapState.class);
        when(currentState.getRoot()).thenReturn(mock(VirtualMap.class));
        @SuppressWarnings("unchecked")
        final StateLifecycleManager<VirtualMapState, VirtualMap> stateLifecycleManager =
                mock(StateLifecycleManager.class);
        reconnectLearner = new ReconnectStateLearner(
                configuration,
                getStaticThreadManager(),
                new DummyConnection(configuration, pairedStreams.getLearnerInput(), pairedStreams.getLearnerOutput()),
                currentState,
                RECONNECT_SOCKET_TIMEOUT,
                mock(ReconnectMetrics.class),
                stateLifecycleManager,
                synchronizer);
    }

    @AfterEach
    void tearDown() throws IOException {
        pairedStreams.close();
    }

    @Test
    @DisplayName("A failure to create the learner fails the reconnect")
    void learnerCreationFailureFailsReconnect() throws Exception {
        final RuntimeException creationFailure = new RuntimeException("test failure");
        when(synchronizer.createLearner(any())).thenThrow(creationFailure);
        sendSignatures();

        final ReconnectStateException e = assertThrows(ReconnectStateException.class, reconnectLearner::execute);

        assertSame(creationFailure, e.getCause(), "the reconnect should fail with the creation failure");
        verifyNotSynchronized();
    }

    @Test
    @DisplayName("If the signatures fail, the learner is closed once it's created")
    void signatureFailureClosesLearnerOnceCreated() throws Exception {
        final CountDownLatch creationAllowed = new CountDownLatch(1);
        when(synchronizer.createLearner(any())).thenAnswer(invocation -> {
            creationAllowed.await();
            return learner;
        });
        // The teacher disconnects instead of sending the signatures
        pairedStreams.disconnectTeacher();

        final Thread reconnectThread = startReconnect(new AtomicReference<>());
        // The reconnect waits for the learner, so that a retry doesn't create a second one
        reconnectThread.join(200);
        assertTrue(reconnectThread.isAlive(), "the reconnect should wait for the learner");
        verify(learner, never()).abortOnException();

        creationAllowed.countDown();
        reconnectThread.join(TIMEOUT_MILLIS);
        assertFalse(reconnectThread.isAlive(), "the reconnect should fail once the learner is created");
        verify(learner).abortOnException();
        verifyNotSynchronized();
    }

    @Test
    @DisplayName("If interrupted while waiting for the learner, the reconnect fails and closes the learner later")
    void interruptWhileWaitingForLearnerClosesLearnerLater() throws Exception {
        final CountDownLatch creationStarted = new CountDownLatch(1);
        final CountDownLatch creationAllowed = new CountDownLatch(1);
        when(synchronizer.createLearner(any())).thenAnswer(invocation -> {
            creationStarted.countDown();
            creationAllowed.await();
            return learner;
        });
        sendSignatures();

        final AtomicReference<Throwable> failure = new AtomicReference<>();
        final Thread reconnectThread = startReconnect(failure);
        assertTrue(creationStarted.await(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS), "the learner should be created");
        reconnectThread.interrupt();

        // The reconnect doesn't wait for the learner after an interrupt
        reconnectThread.join(TIMEOUT_MILLIS);
        assertFalse(reconnectThread.isAlive(), "the reconnect should fail without waiting for the learner");
        assertInstanceOf(ReconnectStateException.class, failure.get());
        verify(learner, never()).abortOnException();

        // The learner is closed when it's ready
        creationAllowed.countDown();
        verify(learner, timeout(TIMEOUT_MILLIS)).abortOnException();
        verifyNotSynchronized();
    }

    /** Sends the teacher's signatures, an empty signature set, to the learner. */
    private void sendSignatures() throws IOException {
        new SigSet().serialize(new WritableStreamingData(teacherConnection.getDos()));
        teacherConnection.getDos().flush();
    }

    /**
     * Runs the reconnect on a new thread.
     *
     * @param failure receives the failure of the reconnect, if any
     * @return the started thread
     */
    private Thread startReconnect(final AtomicReference<Throwable> failure) {
        final Thread thread = new Thread(() -> {
            try {
                reconnectLearner.execute();
            } catch (final Throwable t) {
                failure.set(t);
            }
        });
        thread.start();
        return thread;
    }

    private void verifyNotSynchronized() throws InterruptedException {
        verify(synchronizer, never())
                .synchronize(
                        any(VirtualMapLearner.class), any(DataInputStream.class), any(DataOutputStream.class), any());
    }
}
