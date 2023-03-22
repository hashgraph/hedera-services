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

package com.swirlds.common.threading.interrupt;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.swirlds.common.utility.ThrowingConsumer;
import java.time.Duration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>
 * Utility class for converting interruptable methods into uninterruptable methods.
 * </p>
 *
 * <p>
 * WITH GREAT POWER COMES GREAT RESPONSIBILITY. It's really easy to shoot yourself in the foot with these methods. Be
 * EXTRA confident that you understand the big picture on any thread where you use one of these methods. Incorrectly
 * handing an interrupt can cause a lot of headache.
 * </p>
 */
public final class Uninterruptable {

    private static final Logger logger = LogManager.getLogger(Uninterruptable.class);

    private Uninterruptable() {}

    /**
     * <p>
     * Perform an action. If that action is interrupted, re-attempt that action. If interrupted again then re-attempt
     * again, until the action is eventually successful. Unless this thread is being interrupted many times, the action
     * is most likely to be run 1 or 2 times.
     * </p>
     *
     * <p>
     * This method is useful when operating in a context where it is inconvenient to throw an
     * {@link InterruptedException}, or when performing an action using an interruptable interface but where the
     * required operation is needed to always succeed regardless of interrupts.
     * </p>
     *
     * @param action the action to perform, may be called multiple times if interrupted
     */
    public static void retryIfInterrupted(final InterruptableRunnable action) {
        boolean finished = false;
        boolean interrupted = false;
        while (!finished) {
            try {
                action.run();
                finished = true;
            } catch (final InterruptedException e) {
                interrupted = true;
            }
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * <p>
     * Perform an action that returns a value. If that action is interrupted, re-attempt that action. If interrupted
     * again then re-attempt again, until the action is eventually successful. Unless this thread is being interrupted
     * many times, the action is most likely to be run 1 or 2 times.
     * </p>
     *
     * <p>
     * This method is useful when operating in a context where it is inconvenient to throw an
     * {@link InterruptedException}, or when performing an action using an interruptable interface but where the
     * required operation is needed to always succeed regardless of interrupts.
     * </p>
     *
     * @param action the action to perform, may be called multiple times if interrupted
     */
    public static <T> T retryIfInterrupted(final InterruptableSupplier<T> action) {
        boolean finished = false;
        boolean interrupted = false;
        T value = null;
        while (!finished) {
            try {
                value = action.get();
                finished = true;
            } catch (final InterruptedException e) {
                interrupted = true;
            }
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
        }

        return value;
    }

    /**
     * Perform an action. If the thread is interrupted, the action will be aborted and the thread's interrupt flag will
     * be reset.
     *
     * @param action the action to perform
     */
    public static void abortIfInterrupted(final InterruptableRunnable action) {
        try {
            action.run();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * <p>
     * Perform an action. If the thread is interrupted, the action will be aborted and the thread's interrupt flag will
     * be set. Also writes an error message to the log.
     * </p>
     *
     * <p>
     * This method is useful for situations where interrupts are only expected if there has been an error condition.
     * </p>
     *
     * @param action       the action to perform
     * @param errorMessage the error message to write to the log if this thread is inerrupted
     */
    public static void abortAndLogIfInterrupted(final InterruptableRunnable action, final String errorMessage) {
        try {
            action.run();
        } catch (final InterruptedException e) {
            logger.error(EXCEPTION.getMarker(), errorMessage);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * <p>
     * Pass an object to a consumer that may throw an {@link InterruptedException}. If the thread is interrupted, the
     * action will be aborted and the thread's interrupt flag will be set. Also writes an error message to the log.
     * </p>
     *
     * <p>
     * This method is useful for situations where interrupts are only expected if there has been an error condition.
     * </p>
     *
     * @param consumer     an object that consumes something and may throw an {@link InterruptedException}
     * @param object       the object to pass to the consumer
     * @param errorMessage the error message to write to the log if this thread is inerrupted
     */
    public static <T> void abortAndLogIfInterrupted(
            final ThrowingConsumer<T, InterruptedException> consumer, final T object, final String errorMessage) {

        try {
            consumer.accept(object);
        } catch (final InterruptedException e) {
            logger.error(EXCEPTION.getMarker(), errorMessage);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * <p>
     * Perform an action. If the thread is interrupted, the action will be aborted, the thread's interrupt flag will be
     * set, and an exception will be thrown. Also writes an error message to the log.
     * </p>
     *
     * <p>
     * This method is useful for situations where interrupts are only expected if there has been an error condition and
     * if it is preferred to immediately crash the current thread.
     * </p>
     *
     * @param action       the action to perform
     * @param errorMessage the error message to write to the log if this thread is interrupted
     * @throws IllegalStateException if interrupted
     */
    public static void abortAndThrowIfInterrupted(final InterruptableRunnable action, final String errorMessage) {
        try {
            action.run();
        } catch (final InterruptedException e) {
            logger.error(EXCEPTION.getMarker(), errorMessage);
            Thread.currentThread().interrupt();
            throw new IllegalStateException(errorMessage);
        }
    }

    /**
     * Attempt to sleep for a period of time. If interrupted, the sleep may finish early.
     *
     * @param duration the amount of time to sleep
     */
    public static void tryToSleep(final Duration duration) {
        abortIfInterrupted(() -> MILLISECONDS.sleep(duration.toMillis()));
    }
}
