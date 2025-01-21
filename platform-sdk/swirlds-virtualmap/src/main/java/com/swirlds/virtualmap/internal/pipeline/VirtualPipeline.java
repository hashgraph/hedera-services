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

package com.swirlds.virtualmap.internal.pipeline;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.VIRTUAL_MERKLE_STATS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import com.swirlds.base.function.CheckedSupplier;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.metrics.api.Metrics;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.config.VirtualMapConfig;
import com.swirlds.virtualmap.internal.RecordAccessor;
import com.swirlds.virtualmap.internal.merkle.VirtualMapStatistics;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * <p>
 * Manages the lifecycle of an object that implements {@link VirtualRoot}.
 * </p>
 *
 * <p>
 * This pipeline is responsible for enforcing the following invariants and constraints:
 * </p>
 *
 * <hr>
 * <p><strong>General</strong></p>
 *
 * <ul>
 * 	<li>all copies must be <strong>flushed</strong> or <strong>merged</strong> prior to eviction from memory</li>
 * 	<li>a copy can only be <strong>flushed</strong> or <strong>merged</strong>, not both</li>
 * 	<li>no <strong>flushes</strong> or <strong>merges</strong> are processed during copy detachment</li>
 * 	<li>pipelines can be terminated even when not all copies are destroyed or detached (e.g. during reconnect
 * 		or node shutdown). A terminated pipeline is not required to <strong>flush</strong> or <strong>merge</strong>
 * 		copies before those copies are collected by the java garbage collector.</li>
 * </ul>
 *
 * <hr>
 * <p><strong>Flushing</strong></p>
 * <ul>
 *  <li>only immutable copies can be <strong>flushed</strong></li>
 *  <li>only the oldest released copy can be <strong>flushed</strong></li>
 *  <li>copies with {@link VirtualRoot#shouldBeFlushed()} returning true are guaranteed to be flushed;
 * other copies may be flushed, too</li>
 * 	<li>a copy can be either flushed or merged, but not both</li>
 * </ul>
 *
 * <hr>
 * <p><strong>Merging</strong></p>
 * <ul>
 * 	<li>only destroyed or detached copies can be <strong>merged</strong>
 * 	<li>copies can ony be <strong>merged</strong> into immutable copies</li>
 * 	<li>a copy can be either merged or flushed, but not both</li>
 * </ul>
 *
 * <hr>
 * <p><strong>Hashing</strong></p>
 * <ul>
 * <li>
 * hashes must happen in order, that is the copy from round N must be hashed before the copy from round N+1 is hashed
 * </li>
 * <li>
 * copies must be hashed before they are <strong>flushed</strong>
 * </li>
 * <li>
 * copies must be hashed before they are <strong>merged</strong>
 * </li>
 * <li>
 * the copy that is being <strong>merged</strong> into must be hashed before the merge
 * </li>
 * </ul>
 *
 * <hr>
 * <p><strong>Thread Safety</strong></p>
 * <ul>
 * 	<li><strong>merging</strong> and <strong>flushing</strong> are not thread safe with respect to other
 * 		<strong>merge</strong>/<strong>flush</strong> operations in the general case.</li>
 * 	<li><strong>merged</strong> and <strong>flushing</strong> are not thread safe with respect to hashing on the copies
 * 		being <strong>merged</strong> or <strong>flushed</strong></li>
 * 	<li>terminated pipelines will wait for any <strong>merges</strong> or <strong>flushes</strong> to complete
 * 		before shutting down the pipeline. This method can be called concurrently to all other methods. Any concurrent
 * 		calls that race with this one and come after will not execute.</li>
 * </ul>
 */
public class VirtualPipeline<K extends VirtualKey, V extends VirtualValue> {

    private static final String PIPELINE_COMPONENT = "virtual-pipeline";
    private static final String PIPELINE_THREAD_NAME = "lifecycle";

    private static final Logger logger = LogManager.getLogger(VirtualPipeline.class);

    /**
     * Keeps copies of all {@link VirtualRoot}s that are still part of this pipeline.
     *
     * <p>Copies are removed from this list when destroyed and (flushed or merged).
     */
    private final PipelineList<VirtualRoot<K, V>> copies;

    private final AtomicInteger undestroyedCopies = new AtomicInteger();

    /**
     * A list of copies that have not yet been hashed. We guarantee that each copy
     * is hashed in order from oldest to newest (relying on the order of
     * {@link #registerCopy(VirtualRoot)} to establish that order). Once hashed, the
     * copy is removed from this deque.
     */
    private final ConcurrentLinkedDeque<VirtualRoot<K, V>> unhashedCopies;

    /**
     * A reference to the most recent copy. This is the copy that {@link VirtualRoot#onShutdown(boolean)}
     * will be called on.
     */
    private final AtomicReference<VirtualRoot<K, V>> mostRecentCopy = new AtomicReference<>();

    /**
     * True if the pipeline is alive and running. When set to false, any already scheduled work
     * will still complete. A pipeline is either terminated because the last copy has been destroyed
     * or because of an explicit call to {@link #terminate()}.
     */
    private volatile boolean alive;

    /**
     * A single-threaded executor on which we perform all flush and merge tasks.
     */
    private final ExecutorService executorService;

    /**
     * A flag that indicates whether hash/flush/merge work is scheduled. It's set to true when
     * a new copy is added to the pipeline, and reset to false right before the work is started.
     */
    private final AtomicBoolean workScheduled = new AtomicBoolean(false);

    /**
     * The configuration for this pipeline. To prevent using static configuration calls, we pass it with the constructor.
     */
    private final VirtualMapConfig config;

    private final VirtualMapStatistics statistics;

    /**
     * Create a new pipeline for a family of fast copies on a virtual root.
     */
    public VirtualPipeline(@NonNull final VirtualMapConfig config, @NonNull final String label) {
        this.config = Objects.requireNonNull(config);
        copies = new PipelineList<>();
        unhashedCopies = new ConcurrentLinkedDeque<>();

        alive = true;
        executorService = Executors.newSingleThreadExecutor(new ThreadConfiguration(getStaticThreadManager())
                .setComponent(PIPELINE_COMPONENT)
                .setThreadName(PIPELINE_THREAD_NAME)
                .setExceptionHandler((t, ex) -> logger.error(EXCEPTION.getMarker(), "Uncaught exception ", ex))
                .buildFactory());

        statistics = new VirtualMapStatistics(label);
    }

    /**
     * Register all statistics with an object that manages statistics.
     *
     * @param metrics
     * 		reference to the metrics system
     */
    public void registerMetrics(final Metrics metrics) {
        statistics.registerMetrics(metrics);
    }

    /**
     * Make sure that the given copy is properly registered with this pipeline.
     *
     * @param copy
     * 		the copy in question
     */
    private void validatePipelineRegistration(final VirtualRoot<K, V> copy) {
        if (!copy.isRegisteredToPipeline(this)) {
            throw new IllegalStateException("copy is not registered with this pipeline");
        }
    }

    /**
     * Slow down the fast copy operation if total size of all (unreleased) virtual root copies
     * in this pipeline exceeds {@link VirtualMapConfig#familyThrottleThreshold()}.
     */
    private void applyFamilySizeBackpressure() {
        final long sleepTimeMillis = calculateFamilySizeBackpressurePause();
        if (sleepTimeMillis <= 0) {
            return;
        }

        try {
            final long sleepStartTime = System.currentTimeMillis();
            long timeSleptSoFar;
            do {
                MILLISECONDS.sleep(1);
                timeSleptSoFar = System.currentTimeMillis() - sleepStartTime;
                // Virtual map copy may be flushing on the lifecycle thread, while this thread is
                // sleeping. After any flush, total family size is reduced, and the current thread
                // may not sleep any longer. Re-calculate backpressure duration as of now and
                // check it against the time this thread has slept so far
                final long currentSleepTimeMillis = calculateFamilySizeBackpressurePause();
                if ((currentSleepTimeMillis <= 0) || (timeSleptSoFar >= currentSleepTimeMillis)) {
                    break;
                }
            } while (timeSleptSoFar < sleepTimeMillis);

            // Record actual sleep time
            logger.info(VIRTUAL_MERKLE_STATS.getMarker(), "Total size backpressure: {} ms", timeSleptSoFar);
            statistics.recordFamilySizeBackpressureMs((int) timeSleptSoFar);
        } catch (final InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    long calculateFamilySizeBackpressurePause() {
        final long sizeThreshold = config.familyThrottleThreshold();
        if (sizeThreshold <= 0) {
            return 0;
        }
        final long totalSize = currentTotalSize();
        final double ratio = (double) totalSize / sizeThreshold;
        final int over100percentExcess = (int) Math.round((ratio - 1.0) * 100);
        if (over100percentExcess <= 0) {
            return 0;
        }
        return (long) over100percentExcess * over100percentExcess;
    }

    /**
     * Register a fast copy of the map.
     *
     * @param copy
     * 		a mutable copy of the map
     * @throws NullPointerException
     * 		if the copy is null
     */
    public void registerCopy(final VirtualRoot<K, V> copy) {
        Objects.requireNonNull(copy);

        if (copy.isImmutable()) {
            throw new IllegalStateException("Only mutable copies may be registered");
        }

        // During reconnect, an existing virtual root node may be inserted to a new virtual map node.
        // When it happens, the root node is initialized with {@link VirtualRootNode#postInit()} and
        // requested to register in the same pipeline multiple times
        if (isAlreadyRegistered(copy)) {
            logger.info(VIRTUAL_MERKLE_STATS.getMarker(), "Virtual root copy is already registered in the pipeline");
            return;
        }

        logger.debug(VIRTUAL_MERKLE_STATS.getMarker(), "Register copy {}", copy.getFastCopyVersion());

        undestroyedCopies.getAndIncrement();
        copies.add(copy);
        if (!copy.isHashed()) {
            assert !unhashedCopies.contains(copy);
            unhashedCopies.add(copy);
        }
        mostRecentCopy.set(copy);

        statistics.setPipelineSize(copies.getSize());

        applyFamilySizeBackpressure();
    }

    /**
     * Waits for any pending flushes or merges to complete, and then terminates the pipeline. No
     * further operations will occur.
     */
    public synchronized void terminate() {
        // If we've already shutdown, we can just return. This method is synchronized, and
        // by the time we return this from this method, we will be terminated. So subsequent
        // calls (even races) will see alive as false by that point.
        if (!alive) {
            return;
        }

        pausePipelineAndExecute("terminate", () -> {
            shutdown(false);
            return null;
        });
    }

    /**
     * Destroy a copy of the map. The pipeline may still perform operations on the copy
     * at a later time (i.e. merge and flush), and so this method only gives the guarantee
     * that the resources held by the copy will be eventually destroyed.
     */
    public synchronized void destroyCopy(final VirtualRoot<K, V> copy) {
        if (!alive) {
            // Copy destroyed after the pipeline was manually shut down.
            return;
        }

        logger.debug(VIRTUAL_MERKLE_STATS.getMarker(), "Destroy copy {}", copy.getFastCopyVersion());

        final int remainingCopies = undestroyedCopies.decrementAndGet();
        if (remainingCopies < 0) {
            throw new IllegalStateException("copies destroyed too many times");
        } else if (remainingCopies == 0) {
            // Let pipeline shutdown gracefully, e.g. complete any flushes in progress
            shutdown(false);
        } else {
            scheduleWork();
        }
    }

    /**
     * Ensure that a given copy is hashed. Will not re-hash if map is already hashed.
     * Will cause older copies of the map to be hashed if they have not yet been hashed.
     *
     * @param copy
     * 		a copy of the map that needs to be hashed
     */
    public void hashCopy(final VirtualRoot<K, V> copy) {
        validatePipelineRegistration(copy);

        for (; ; ) {
            final VirtualRoot<K, V> unhashedCopy = unhashedCopies.peekFirst();
            if (unhashedCopy == null) {
                break;
            }
            synchronized (unhashedCopy) {
                if (copy.isHashed()) {
                    return;
                }
                // If two threads are in hashCopy() in parallel, for the same copy or different ones,
                // the chances are they can peek the same unhashedCopy from unhashedCopies. When it
                // happens, one thread will wait until the other thread is hashing. The copy is
                // removed from unhashedCopies, but the first thread has already grabbed the reference
                // outside of the synchronized block. When it finally enters the block, unhashedCopy
                // is already hashed by the other thread
                if (!unhashedCopy.isHashed()) {
                    unhashedCopy.computeHash();
                }
                assert unhashedCopy.isHashed();
                unhashedCopies.remove(unhashedCopy);
            }
        }
        if (!copy.isHashed()) {
            throw new IllegalStateException("failed to hash copy");
        }
    }

    public <T, E extends Exception> T pausePipelineAndRun(final String label, final CheckedSupplier<T, E> action)
            throws E {
        final T ret = pausePipelineAndExecute(label, action);
        if (alive) {
            scheduleWork();
        }
        return ret;
    }

    /**
     * Put a copy into a detached state. A detached copy will split off from the regular chain of caches. This
     * allows for merges and flushes to continue even if this copy is long-lived.
     *
     * <p>This method waits for the current pipeline job to complete, then puts the pipeline on hold, and
     * calls copy's {@link VirtualRoot#detach()} method on the current thread. It prevents any merging of
     * flushing while the snapshot is being taken. Then the pipeline is resumed.
     *
     * @param copy
     * 		the copy to detach
     * @return a reference to the detached state
     */
    public RecordAccessor<K, V> detachCopy(final VirtualRoot<K, V> copy) {
        validatePipelineRegistration(copy);
        final RecordAccessor<K, V> ret = pausePipelineAndExecute("detach", copy::detach);
        if (alive) {
            scheduleWork();
        }
        return ret;
    }

    /**
     * Takes a snapshot of the given copy to the specified directory.
     *
     * <p>This method waits for the current pipeline job to complete, then puts the pipeline on hold, and
     * calls copy's {@link VirtualRoot#detach()} method on the current thread. It prevents any merging of
     * flushing while the snapshot is being taken. Then the pipeline is resumed.
     *
     * @param copy
     * 		The copy. Cannot be null. Should be a member of this pipeline, but technically doesn't need to be.
     * @param targetDirectory
     * 		the location where detached files are written. If null then default location is used.
     */
    public void snapshot(final VirtualRoot<K, V> copy, final Path targetDirectory) throws IOException {
        validatePipelineRegistration(copy);
        pausePipelineAndExecute("snapshot", () -> {
            copy.snapshot(targetDirectory);
            return null;
        });
        if (alive) {
            scheduleWork();
        }
    }

    /**
     * Posts a new hash/flush/merge job to the lifecycle thread executor, if no job has been
     * scheduled yet.
     */
    private void scheduleWork() {
        if (workScheduled.compareAndSet(false, true)) {
            executorService.submit(this::doWork);
        }
    }

    /**
     * Wait until the pipeline thread has finished and then return.
     *
     * @param timeout
     * 		the magnitude of the timeout
     * @param unit
     * 		the unit for timeout
     * @return true if the executor service terminated, false if it has not yet terminated when the timeout expired
     * @throws InterruptedException
     * 		if calling thread is interrupted
     */
    public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
        return executorService.awaitTermination(timeout, unit);
    }

    /**
     * Check if this copy should be flushed.
     */
    private boolean shouldBeFlushed(final VirtualRoot<K, V> copy) {
        return copy.shouldBeFlushed() // either explicitly marked to flush or based on its size
                && (copy.isDestroyed() || copy.isDetached()); // destroyed or detached
    }

    /**
     * Total estimated size of all copies currently registered in this pipeline.
     *
     * @return Total estimated size
     */
    private long currentTotalSize() {
        long totalEstimatedSize = 0;
        for (PipelineListNode<VirtualRoot<K, V>> node = copies.getFirst(); node != null; node = node.getNext()) {
            final VirtualRoot<K, V> copy = node.getValue();
            if (!copy.isImmutable()) {
                break;
            }
            final long estimatedSize = copy.estimatedSize();
            totalEstimatedSize += estimatedSize;
        }
        return totalEstimatedSize;
    }

    /**
     * Flush a copy. Hash it if necessary.
     *
     * @param copy the copy to flush
     */
    private void flush(final VirtualRoot<K, V> copy) {
        if (copy.isFlushed()) {
            throw new IllegalStateException("copy is already flushed");
        }
        if (!copy.isHashed()) {
            hashCopy(copy);
        }
        copy.flush();
    }

    /**
     * Copies can only be merged into younger copies that are themselves immutable. Check if that is the case.
     */
    private boolean canBeMerged(final PipelineListNode<VirtualRoot<K, V>> mergeCandidate) {
        final VirtualRoot<K, V> copy = mergeCandidate.getValue();
        final PipelineListNode<VirtualRoot<K, V>> mergeTarget = mergeCandidate.getNext();

        return !copy.shouldBeFlushed() // shouldn't be flushed
                && (copy.isDestroyed() || copy.isDetached()) // copy must be destroyed or detached
                && mergeTarget != null // target must exist
                && mergeTarget.getValue().isImmutable(); // target must be immutable
    }

    /**
     * Merge a copy. Hash it if necessary. This method will not be called for any copy
     * that does not have a valid merge target (i.e. an immutable one).
     *
     * @param node
     * 		the node containing the copy to merge
     */
    private void merge(final PipelineListNode<VirtualRoot<K, V>> node) {
        final VirtualRoot<K, V> copy = node.getValue();

        if (copy.isMerged()) {
            throw new IllegalStateException("copy is already merged");
        }

        if (!copy.isHashed()) {
            hashCopy(copy);
        }

        final VirtualRoot<K, V> next = node.getNext().getValue();
        if (!next.isHashed()) {
            hashCopy(next);
        }

        copy.merge();
    }

    /**
     * Hash, flush, and merge all copies currently capable of these operations.
     */
    private void hashFlushMerge() {
        PipelineListNode<VirtualRoot<K, V>> next = copies.getFirst();
        // Iterate from the oldest copy to the newest
        while ((next != null) && !Thread.currentThread().isInterrupted()) {
            final VirtualRoot<K, V> copy = next.getValue();
            // The newest copy. Nothing can be done to it
            if (!copy.isImmutable()) {
                break;
            }
            if ((next == copies.getFirst()) && shouldBeFlushed(copy)) {
                logger.debug(VIRTUAL_MERKLE_STATS.getMarker(), "Flush {}", copy.getFastCopyVersion());
                flush(copy);
                copies.remove(next);
            } else if (canBeMerged(next)) {
                assert !copy.isMerged();
                logger.debug(VIRTUAL_MERKLE_STATS.getMarker(), "Merge {}", copy.getFastCopyVersion());
                merge(next);
                copies.remove(next);
            }
            statistics.setPipelineSize(copies.getSize());
            final long totalSize = currentTotalSize();
            statistics.setNodeCacheSize(totalSize);
            next = next.getNext();
        }
    }

    private void doWork() {
        workScheduled.set(false);
        try {
            hashFlushMerge();
        } catch (final Throwable e) { // NOSONAR: Must cleanup and log if an error occurred since this is on a thread.
            logger.error(EXCEPTION.getMarker(), "exception on virtual pipeline thread", e);
            shutdown(true);
        }
    }

    /**
     * Shutdown the executor service.
     *
     * @param immediately
     * 		If {@code true}, shuts down the service immediately. This will interrupt any threads currently
     * 		running. Useful for when there is an error, or for when the virtual map is no longer in use
     * 		(and therefore any/all pending work will never be used).
     */
    private synchronized void shutdown(final boolean immediately) {
        alive = false;
        if (!executorService.isShutdown()) {
            if (immediately) {
                executorService.shutdownNow();
                fireOnShutdown(immediately);
            } else {
                executorService.submit(() -> fireOnShutdown(false));
                executorService.shutdown();
            }
        }
    }

    /**
     * Waits for any pending flushes or merges to complete and then pauses the pipeline while the
     * given supplier provides a value, and then resumes pipeline operation. Fatal errors happen
     * if the background thread is interrupted.
     *
     * @param label
     * 		A log/error friendly label to describe the runnable
     * @param supplier
     * 		The supplier. Cannot be null.
     */
    <V, E extends Exception> V pausePipelineAndExecute(final String label, final CheckedSupplier<V, E> supplier)
            throws E {
        Objects.requireNonNull(supplier);
        final CountDownLatch waitForBackgroundThreadToStart = new CountDownLatch(1);
        final CountDownLatch waitForRunnableToFinish = new CountDownLatch(1);
        executorService.execute(() -> {
            waitForBackgroundThreadToStart.countDown();

            try {
                waitForRunnableToFinish.await();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(
                        "Fatal error: interrupted while waiting for runnable " + label + " to finish");
            }
        });

        try {
            waitForBackgroundThreadToStart.await();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Fatal error: failed to start " + label);
        }

        try {
            return supplier.get();
        } finally {
            waitForRunnableToFinish.countDown();
        }
    }

    /**
     * Gets whether this pipeline has been terminated.
     *
     * @return True if this pipeline has been terminated.
     */
    public boolean isTerminated() {
        return !alive;
    }

    /**
     * If there is a most-recent copy, calls shutdown on it.
     *
     * @param immediately
     * 		if true then the shutdown is immediate
     */
    private void fireOnShutdown(final boolean immediately) {
        final var copy = mostRecentCopy.get();
        if (copy != null) {
            copy.onShutdown(immediately);
        }
    }

    private static String uppercaseBoolean(final boolean value) {
        return value ? "TRUE" : "FALSE";
    }

    /**
     * This method dumps data about the current state of the pipeline to the log. Useful in emergencies
     * when debugging pipeline failures.
     */
    public void logDebugInfo() {

        final StringBuilder sb = new StringBuilder();

        sb.append("Virtual pipeline dump, ");

        sb.append("  size = ").append(copies.getSize()).append("\n");
        sb.append("Copies listed oldest to newest:\n");

        PipelineListNode<VirtualRoot<K, V>> next = copies.getFirst();
        int index = 0;
        while (next != null) {
            final VirtualRoot<K, V> copy = next.getValue();

            sb.append(index);
            sb.append(", should be flushed = ").append(uppercaseBoolean(shouldBeFlushed(copy)));
            sb.append(", can be merged = ").append(uppercaseBoolean(canBeMerged(next)));
            sb.append(", flushed = ").append(uppercaseBoolean(copy.isFlushed()));
            sb.append(", destroyed = ").append(uppercaseBoolean(copy.isDestroyed()));
            sb.append(", hashed = ").append(uppercaseBoolean(copy.isHashed()));
            sb.append(", detached = ").append(uppercaseBoolean(copy.isDetached()));
            sb.append("\n");

            index++;
            next = next.getNext();
        }

        sb.append("There is no problem if this has happened during a freeze.\n");
        logger.info(VIRTUAL_MERKLE_STATS.getMarker(), "{}", sb);
    }

    /**
     * Checks if the copy is already registered in this pipeline.
     *
     * There is a similar method in VirtualRootNode, but it only checks the VirtualPipeline
     * but not if it actually contains the copy.
     *
     * @param copy
     * 		Virtual root copy to check
     * @return
     *        True, if this pipeline already has the copy registered, false otherwise
     */
    private boolean isAlreadyRegistered(final VirtualRoot<K, V> copy) {
        return !copies.testAll(c -> !copy.equals(c));
    }
}
