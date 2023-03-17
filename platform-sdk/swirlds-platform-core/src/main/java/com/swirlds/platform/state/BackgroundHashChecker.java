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

package com.swirlds.platform.state;

import static com.swirlds.common.merkle.hash.MerkleHashChecker.checkHashAndLog;
import static com.swirlds.platform.SwirldsPlatform.PLATFORM_THREAD_POOL_NAME;

import com.swirlds.common.threading.framework.StoppableThread;
import com.swirlds.common.threading.framework.config.StoppableThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.platform.state.signed.SignedState;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A debug utility that checks the hashes of states in a background thread.
 */
public class BackgroundHashChecker {

    private final Supplier<AutoCloseableWrapper<SignedState>> stateSupplier;
    private SignedState previousState;

    private final Consumer<SignedState> passedValidationCallback;
    private final Consumer<SignedState> failedValidationCallback;

    private final StoppableThread thread;

    private static final int WAIT_FOR_NEW_STATE_PERIOD_MS = 100;

    /**
     * Create a new background hash checker. This constructor starts a background thread.
     *
     * @param threadManager
     * 		responsible for creating and managing threads
     * @param stateSupplier
     * 		a method that is used to get signed states
     */
    public BackgroundHashChecker(
            final ThreadManager threadManager, final Supplier<AutoCloseableWrapper<SignedState>> stateSupplier) {
        this(threadManager, stateSupplier, null, null);
    }

    /**
     * Create a new background hash checker. This constructor starts a background thread.
     *
     * @param threadManager
     * 		responsible for creating threads
     * @param stateSupplier
     * 		a method that is used to get signed states
     * @param passedValidationCallback
     * 		this method his called with each signed state that passes validation. State passed to this callback
     * 		should not be used once the callback returns. A null callback is permitted.
     * @param failedValidationCallback
     * 		this method his called with each signed state that fails validation. State passed to this callback
     * 		should not be used once the callback returns. A null callback is permitted.
     */
    public BackgroundHashChecker(
            final ThreadManager threadManager,
            final Supplier<AutoCloseableWrapper<SignedState>> stateSupplier,
            final Consumer<SignedState> passedValidationCallback,
            final Consumer<SignedState> failedValidationCallback) {

        this.stateSupplier = stateSupplier;
        this.passedValidationCallback = passedValidationCallback;
        this.failedValidationCallback = failedValidationCallback;

        this.thread = new StoppableThreadConfiguration<>(threadManager)
                .setComponent(PLATFORM_THREAD_POOL_NAME)
                .setThreadName("background-hash-checker")
                .setPriority(Thread.MIN_PRIORITY)
                .setWork(this::doWork)
                .build();
        this.thread.start();
    }

    /**
     * Stop the background thread. Once stopped can not be restarted.
     */
    public void stop() {
        thread.stop();
    }

    /**
     * Join the background thread.
     */
    public void join() throws InterruptedException {
        thread.join();
    }

    /**
     * Join the background thread with a given timeout.
     *
     * @param millis
     * 		the amount of time to wait to join
     */
    public void join(final long millis) throws InterruptedException {
        thread.join(millis);
    }

    /**
     * Check if the background thread is alive.
     */
    public boolean isAlive() {
        return thread.isAlive();
    }

    private void doWork() throws InterruptedException {
        try (final AutoCloseableWrapper<SignedState> wrapper = stateSupplier.get()) {

            final SignedState signedState = wrapper.get();
            if (signedState == previousState || signedState == null) {
                Thread.sleep(WAIT_FOR_NEW_STATE_PERIOD_MS);
                return;
            }
            previousState = signedState;

            final State state = wrapper.get().getState();
            final boolean passed = checkHashAndLog(
                    state,
                    "background state hash check, round = "
                            + state.getPlatformState().getPlatformData().getRound(),
                    10);

            if (passed) {
                if (passedValidationCallback != null) {
                    passedValidationCallback.accept(signedState);
                }
            } else {
                if (failedValidationCallback != null) {
                    failedValidationCallback.accept(signedState);
                }
            }
        }
    }
}
