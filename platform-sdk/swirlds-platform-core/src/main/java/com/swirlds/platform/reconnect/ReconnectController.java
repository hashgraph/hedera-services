/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.reconnect;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.RECONNECT;

import com.swirlds.common.threading.BlockingResourceProvider;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.threading.locks.locked.LockedResource;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.logging.LogMarker;
import com.swirlds.platform.Connection;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.state.signed.SignedStateValidator;
import com.swirlds.platform.system.SystemExitReason;
import com.swirlds.platform.system.SystemUtils;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Responsible for executing the whole reconnect process
 */
public class ReconnectController implements Runnable {
    private static final Logger logger = LogManager.getLogger(ReconnectController.class);
    private static final int FAILED_RECONNECT_SLEEP_MILLIS = 1000;

    private final ReconnectHelper helper;
    private final Semaphore threadRunning;
    private final BlockingResourceProvider<Connection> connectionProvider;
    private final Runnable startChatter;
    private final AtomicReference<SignedStateValidator> validator = new AtomicReference<>();
    private final ThreadManager threadManager;

    /**
     * @param threadManager
     * 		responsible for creating and managing threads
     * @param helper
     * 		executes phases of a reconnect
     * @param startChatter
     * 		starts chatter if previously suspended
     */
    public ReconnectController(
            final ThreadManager threadManager, final ReconnectHelper helper, final Runnable startChatter) {
        this.threadManager = threadManager;
        this.helper = helper;
        this.startChatter = startChatter;
        this.threadRunning = new Semaphore(1);
        this.connectionProvider = new BlockingResourceProvider<>();
    }

    /**
     * Starts the reconnect controller thread if it's not already running
     */
    public void start() {
        if (!threadRunning.tryAcquire()) {
            logger.error(EXCEPTION.getMarker(), "Attempting to start reconnect controller while its already running");
            return;
        }
        logger.info(LogMarker.RECONNECT.getMarker(), "Starting ReconnectController");
        new ThreadConfiguration(threadManager)
                .setComponent("reconnect")
                .setThreadName("reconnect-controller")
                .setRunnable(this)
                .build(true /*start*/);
    }

    @Override
    public void run() {
        try {
            // the ReconnectHelper uses a ReconnectLearnerThrottle to exit if there are too many failed attempts
            // so in this thread we can just try until it succeeds or the throttle kicks in
            while (!executeReconnect()) {
                logger.error(LogMarker.RECONNECT.getMarker(), "Reconnect failed, retrying");
                Thread.sleep(FAILED_RECONNECT_SLEEP_MILLIS);
            }
        } catch (final RuntimeException | InterruptedException e) {
            logger.error(EXCEPTION.getMarker(), "Unexpected error occurred while reconnecting", e);
            SystemUtils.exitSystem(SystemExitReason.RECONNECT_FAILURE);
        } finally {
            threadRunning.release();
        }
    }

    private boolean executeReconnect() throws InterruptedException {
        helper.prepareForReconnect();

        final SignedState signedState;
        logger.info(RECONNECT.getMarker(), "waiting for reconnect connection");
        try (final LockedResource<Connection> connection = connectionProvider.waitForResource()) {
            logger.info(RECONNECT.getMarker(), "acquired reconnect connection");
            signedState = helper.receiveSignedState(connection.getResource(), validator.get());
        } catch (final RuntimeException e) {
            logger.info(RECONNECT.getMarker(), "receiving signed state failed", e);
            return false;
        }
        if (!helper.loadSignedState(signedState)) {
            return false;
        }
        startChatter.run();
        return true;
    }

    /**
     * Try to acquire a permit for negotiate a reconnect in the role of the learner
     *
     * @return true if the permit has been acquired
     */
    public boolean acquireLearnerPermit() {
        return connectionProvider.acquireProvidePermit();
    }

    /**
     * Releases a previously acquired permit for reconnect
     */
    public void cancelLearnerPermit() {
        connectionProvider.releaseProvidePermit();
    }

    /**
     * Provides a connection over which a reconnect learner has been already negotiated. This method should only be
     * called if {@link #acquireLearnerPermit()} has returned true previously. This method blocks until the reconnect is
     * done.
     *
     * @param connection
     * 		the connection to use to execute the reconnect learner protocol
     * @throws InterruptedException
     * 		if the calling thread is interrupted while the connection is being used
     */
    public void provideLearnerConnection(final Connection connection) throws InterruptedException {
        connectionProvider.provide(connection);
    }

    /**
     * Sets the validator used to determine if the state received in reconnect has sufficient valid signatures.
     *
     * @param validator
     * 		the validator to use
     */
    public void setStateValidator(final SignedStateValidator validator) {
        this.validator.set(validator);
    }
}
