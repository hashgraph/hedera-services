// SPDX-License-Identifier: Apache-2.0
package com.swirlds.virtualmap.internal;

import static com.swirlds.logging.legacy.LogMarker.VIRTUAL_MERKLE_STATS;
import static org.hiero.base.concurrent.manager.AdHocThreadManager.getStaticThreadManager;

import com.swirlds.virtualmap.MerklePathUtils;
import com.swirlds.virtualmap.datasource.VirtualDataSource;
import com.swirlds.virtualmap.datasource.VirtualHashChunk;
import com.swirlds.virtualmap.datasource.VirtualLeafBytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Marker;
import org.hiero.base.concurrent.framework.config.CompositeThreadNameProvider;
import org.hiero.base.concurrent.framework.config.ThreadConfiguration;

/// This is a mechanism to flush data (dirty hashes, dirty leaves, deleted leaves) to disk
/// outside of the virtual map cache and pipeline.
///
/// This flusher is thread safe, its methods like [#updateHashChunk(VirtualHashChunk)],
/// [#updateLeaf(VirtualLeafBytes)], and [#deleteLeaf(VirtualLeafBytes)] can be called from multiple
/// threads (producers). Producers only add data to the current batch. Full batches are flushed on a
/// single-threaded executor, created in the constructor, one at a time, in the order they are filled.
/// The final flush runs in [#finish()] in the calling thread, after the previous flush is complete.
///
/// [#init(long, long)] must be called before any producers start appending changes, and [#finish()] must be
/// called in the end, after all producers are done. If the job is aborted instead, [#cancel()] must be called,
/// possibly even before [#init(long, long)]. Either of them shuts down the executor.
///
/// Memory is bounded by backpressure: at most one flush is running or queued at a time. A batch that becomes
/// full while a flush is running stays in memory, till the next producer call after the flush is complete
/// submits it, or till [#finish()] flushes it. Until then, producers block instead of adding to the full batch.
/// So at most about two batches are kept in memory, one being flushed and one being filled. Producers block in
/// [Future#get()] of the running flush. It parks the thread without `ForkJoinPool.managedBlock()`, so that
/// ForkJoinPool workers don't spawn compensation threads, and it never runs other tasks.
///
/// Deadlock freedom relies on producers waiting for flush tasks only, while flush tasks never wait for
/// producers. Flushes don't run on producer threads, so a producer that is a ForkJoinPool worker can't be
/// made to run another producer task, while it waits inside a flush (FJ helping), and then block on the
/// flush it runs itself. The final flush in [#finish()] may run on such a worker, but it's safe, since all
/// producers are done by then. The data source must never call back into this flusher.
///
/// If a flush fails, the failure is sticky. Once the failed flush is complete, no new flushes are started,
/// producers blocked on backpressure are released, and all subsequent calls, including [#finish()], throw an
/// [IllegalStateException] with the failure as the cause. Data in the failed batch and in the batches after it
/// is lost, so the caller must treat it as a failure of the whole job. The failure isn't logged here, this is
/// up to the caller, unless this flusher is already cancelled, and there is no caller to report it to.
///
/// If the job that feeds this flusher fails or times out, it must call [#cancel()]. Producers blocked on
/// backpressure or in [#finish()] are released, and all subsequent calls throw an [IllegalStateException]
/// with a [CancellationException] as the cause. A flush that is already running isn't interrupted, but no new
/// flushes are started. Cancellation only unblocks and fails calls into this flusher, the caller is still
/// responsible for stopping the producers.
public class HashLeafFlusher {

    private static final Logger logger = LogManager.getLogger(HashLeafFlusher.class);

    // The leaf path range, set in init(). Visible to flush tasks, since they are submitted after the range
    // is set, and to the thread that calls finish(), since both methods are synchronized. Using 0 as a flag
    // that the path range is not set, since -1,-1 is a valid (empty) range
    private long firstLeafPath = 0;
    private long lastLeafPath = 0;

    private final VirtualDataSource dataSource;

    private final int hashChunkHeight;

    private final int flushInterval;

    private final VirtualMapStatistics statistics;

    private final Marker logMarker;

    // Runs all flushes, except the final one. Its thread is only started on the first submitted flush. Shut
    // down by cancel() or finish(), always with the lock held. Once shut down, no more flushes are submitted.
    // While producers may still call this flusher, i.e. before finish(), shut down means cancelled
    private final ExecutorService executor;

    // The batch that is currently being filled. Null before init() and after finish(). Synchronized on this
    private Batch batch;

    // The last submitted flush, or null if no flush has been submitted yet. Never cleared, a flush is in progress
    // while it isn't done, see isFlushInProgress(). If it has failed, no new flushes are started, and its failure
    // is rethrown to all subsequent calls. Synchronized on this
    private Future<?> flushingFuture;

    // When producers started blocking on backpressure, or 0 if none are blocked. Synchronized on this
    private long backpressureSince = 0;

    // Total time producers were blocked on backpressure, logged in finish() or cancel(). A long total may
    // indicate a slow disk, and it may make reconnect fail, since a blocked learner stops reading from the
    // teacher, which times out. Synchronized on this
    private long backpressureTotalMs = 0;

    // Passed to the data source with every flush, see VirtualDataSource.saveRecords()
    private final boolean reconnectContext;

    // Data accumulated for a single flush
    private final class Batch {
        final List<VirtualHashChunk> hashChunks = new ArrayList<>();
        final List<VirtualLeafBytes> leaves = new ArrayList<>();
        final List<VirtualLeafBytes> deletedLeaves = new ArrayList<>();

        // Must be called with the lock held
        private boolean isFull() {
            // If flushInterval isn't positive, all data is flushed in finish() only
            return flushInterval > 0
                    && ((hashChunks.size() * VirtualHashChunk.getChunkSize(hashChunkHeight) >= flushInterval)
                            || (leaves.size() >= flushInterval)
                            || (deletedLeaves.size() >= flushInterval));
        }
    }

    /// Creates a flusher that logs with the `VIRTUAL_MERKLE_STATS` marker and doesn't flush in the
    /// reconnect context.
    ///
    /// @param dataSource the data source to flush data to
    /// @param flushInterval the flush threshold, see
    ///     [#HashLeafFlusher(VirtualDataSource, int, VirtualMapStatistics, Marker, boolean)]
    /// @param statistics virtual map statistics to record flush durations
    public HashLeafFlusher(
            @NonNull final VirtualDataSource dataSource,
            final int flushInterval,
            @NonNull final VirtualMapStatistics statistics) {
        this(dataSource, flushInterval, statistics, VIRTUAL_MERKLE_STATS.getMarker(), false);
    }

    /// Creates a flusher that logs with the given marker.
    ///
    /// @param dataSource the data source to flush data to
    /// @param flushInterval the flush threshold. A flush is started once any of the following reaches this
    ///     value: the number of hash slots in collected hash chunks (number of chunks times the chunk size),
    ///     the number of updated leaves, or the number of deleted leaves. The same threshold is used for
    ///     backpressure. If zero or negative, all data is flushed in [#finish()] only, and producers never block
    /// @param statistics virtual map statistics to record flush durations
    /// @param logMarker the marker to use for flush logs
    /// @param reconnectContext whether data is flushed in the reconnect context. Passed to the data source
    ///     with every flush, see [VirtualDataSource#saveRecords]. Affects how leaves are deleted, see
    ///     [#deleteLeaf(VirtualLeafBytes)]
    public HashLeafFlusher(
            @NonNull final VirtualDataSource dataSource,
            final int flushInterval,
            @NonNull final VirtualMapStatistics statistics,
            @NonNull final Marker logMarker,
            final boolean reconnectContext) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.hashChunkHeight = this.dataSource.getHashChunkHeight();
        this.flushInterval = flushInterval;
        this.statistics = Objects.requireNonNull(statistics, "statistics must not be null");
        this.logMarker = Objects.requireNonNull(logMarker, "logMarker must not be null");
        this.reconnectContext = reconnectContext;
        this.executor = Executors.newSingleThreadExecutor(new ThreadConfiguration(getStaticThreadManager())
                .setSingleThreadName(CompositeThreadNameProvider.create("virtualmap", "hash-leaf-flusher"))
                .buildFactory());
    }

    /// Initializes this flusher with the leaf path range of the target tree. Must be called once, before any
    /// other methods, except [#cancel()]. The range is passed to the data source with every flush.
    ///
    /// @param firstLeafPath the first leaf path, or [MerklePathUtils#INVALID_PATH] if the tree is empty
    /// @param lastLeafPath the last leaf path, or [MerklePathUtils#INVALID_PATH] if the tree is empty
    /// @throws IllegalArgumentException if the path range is invalid, or if this flusher is already initialized
    public synchronized void init(final long firstLeafPath, final long lastLeafPath) {
        if (firstLeafPath != MerklePathUtils.INVALID_PATH && !(firstLeafPath > 0 && firstLeafPath <= lastLeafPath)) {
            throw new IllegalArgumentException("The first leaf path is invalid. firstLeafPath=" + firstLeafPath
                    + ", lastLeafPath=" + lastLeafPath);
        }
        if (lastLeafPath != MerklePathUtils.INVALID_PATH && lastLeafPath <= 0) {
            throw new IllegalArgumentException(
                    "The last leaf path is invalid. firstLeafPath=" + firstLeafPath + ", lastLeafPath=" + lastLeafPath);
        }
        if ((this.firstLeafPath != 0) && (this.lastLeafPath != 0)) {
            throw new IllegalArgumentException("Flusher was already initialized. firstLeafPath=" + this.firstLeafPath
                    + ", lastLeafPath=" + this.lastLeafPath);
        }

        this.firstLeafPath = firstLeafPath;
        this.lastLeafPath = lastLeafPath;

        batch = new Batch();

        logger.info(
                logMarker,
                "Hash leaf flusher initialized with firstLeafPath={}, lastLeafPath={}",
                firstLeafPath,
                lastLeafPath);
    }

    /// Adds an updated hash chunk to the current batch. May be called from multiple threads.
    ///
    /// If the current batch is already due for a flush, while the previous flush is still in progress, this
    /// method blocks till the previous flush is complete. Never runs a flush in the current thread.
    ///
    /// @param chunk the hash chunk to save
    /// @throws IllegalStateException if this flusher is not initialized or already finished, if it's cancelled,
    ///     if a flush has failed, or if the current thread is interrupted while blocked. The interrupted flag is
    ///     left set
    public void updateHashChunk(@NonNull final VirtualHashChunk chunk) {
        addToBatch(batch -> batch.hashChunks.add(chunk));
    }

    /// Adds an updated leaf to the current batch. May be called from multiple threads. Blocks the same way
    /// as [#updateHashChunk(VirtualHashChunk)].
    ///
    /// @param leaf the leaf to save
    /// @throws IllegalStateException if this flusher is not initialized or already finished, if it's cancelled,
    ///     if a flush has failed, or if the current thread is interrupted while blocked. The interrupted flag is
    ///     left set
    public void updateLeaf(final VirtualLeafBytes<?> leaf) {
        addToBatch(batch -> batch.leaves.add(leaf));
    }

    /// Adds a leaf to delete to the current batch. May be called from multiple threads. Blocks the same way
    /// as [#updateHashChunk(VirtualHashChunk)].
    ///
    /// In the reconnect context, the leaf is a candidate for removal only. During the flush, its key is
    /// removed from the data source only if the key is still mapped to the leaf path, so a key that has
    /// been moved to a different path isn't lost. Otherwise, the key is removed unconditionally.
    ///
    /// @param leaf the leaf to delete
    /// @throws IllegalStateException if this flusher is not initialized or already finished, if it's cancelled,
    ///     if a flush has failed, or if the current thread is interrupted while blocked. The interrupted flag is
    ///     left set
    public void deleteLeaf(final VirtualLeafBytes<?> leaf) {
        addToBatch(batch -> batch.deletedLeaves.add(leaf));
    }

    /// Cancels this flusher. Threads blocked on backpressure or in [#finish()] are released, and all
    /// subsequent calls to [#updateHashChunk(VirtualHashChunk)], [#updateLeaf(VirtualLeafBytes)],
    /// [#deleteLeaf(VirtualLeafBytes)], and [#finish()] throw an [IllegalStateException] with a
    /// [CancellationException] as the cause. A flush that is already running isn't interrupted, but no new
    /// flushes are started, and the executor thread exits once the running flush is complete. May be called
    /// multiple times, from any thread, at any moment, including before [#init(long, long)]. Never waits for
    /// a running flush. On the first call, if this flusher is initialized and not finished yet, the total time
    /// producers were blocked on backpressure is logged.
    ///
    /// This method only affects calls into this flusher. It doesn't stop producers themselves, this is the
    /// caller's responsibility.
    public synchronized void cancel() {
        final boolean alreadyShutDown = executor.isShutdown();
        // Subsequent calls fail, see throwIfCancelledOrFailed()
        executor.shutdown();
        if (flushingFuture != null) {
            // Releases threads blocked waiting for a flush. The running flush, if any, isn't interrupted
            flushingFuture.cancel(false);
        }
        // Not initialized, already finished, or already cancelled, nothing to log
        if ((batch != null) && !alreadyShutDown) {
            logBackpressureTotal();
        }
    }

    /// Shuts down the executor, waits for a flush that is already running, if any, and then flushes all
    /// remaining data in the current thread. The final flush is run even if there is no data, so the data source
    /// is always updated with the leaf path range from [#init(long, long)]. Must be called once, after all calls
    /// to [#updateHashChunk(VirtualHashChunk)], [#updateLeaf(VirtualLeafBytes)], and
    /// [#deleteLeaf(VirtualLeafBytes)] have returned.
    ///
    /// @throws RuntimeException if the final flush fails
    /// @throws IllegalStateException if this flusher is cancelled, if this flusher is not initialized or already
    ///     finished, if a flush has failed, or if the current thread is interrupted while waiting for the running
    ///     flush or while running the final flush. The interrupted flag is left set
    public void finish() {
        final Batch last;
        final Future<?> running;
        synchronized (this) {
            throwIfNotInitializedOrFinished();
            last = batch;
            batch = null;
            running = flushingFuture;
            // Log before throwIfCancelledOrFailed(), so the total is logged even if a flush has failed. If
            // cancelled, it's already logged in cancel()
            if (!executor.isShutdown()) {
                logBackpressureTotal();
            }
            // Check before the executor is shut down here, as a shut down executor means cancelled. Flushes are
            // submitted by producers only, and they are done by now, so the running flush, if any, is the last one.
            // It may be already complete. The current batch may be full, then it's flushed below
            try {
                throwIfCancelledOrFailed();
            } finally {
                executor.shutdown();
            }
        }
        if (running != null) {
            // Wait outside of the synchronized block, so cancel() isn't blocked, and can release this thread
            awaitFlush(running);
        }
        // Nodes / leaves lists may be empty, but a flush is still needed to make sure all stale leaves are
        // removed from the data source. The flush is run outside the synchronized block, so cancel() isn't
        // blocked till the final flush is complete
        flush(last);
    }

    private void addToBatch(final Consumer<Batch> batchConsumer) {
        // Wait outside of the synchronized block, so other producers block the same way, and cancel() isn't
        // blocked. After every wait, try again, since other producers may have filled the next batch, or this
        // flusher may have been cancelled or failed
        Future<?> running;
        while ((running = tryAddToBatch(batchConsumer)) != null) {
            awaitFlush(running);
        }
    }

    // Adds to the current batch and returns null, or returns the flush to wait for, if the current batch is full,
    // but the previous flush is still in progress. Producers never add to a batch that is already full
    private synchronized Future<?> tryAddToBatch(final Consumer<Batch> batchConsumer) {
        // Checked before throwIfCancelledOrFailed(), so a call after finish() isn't reported as cancelled
        throwIfNotInitializedOrFinished();
        throwIfCancelledOrFailed();

        if (batch.isFull()) {
            if (isFlushInProgress()) {
                // Backpressure: the batch is full, but it can't be flushed yet
                if (backpressureSince == 0) {
                    backpressureSince = System.currentTimeMillis();
                }
                return flushingFuture;
            }
            // The batch became full while the previous flush was in progress
            submitFlush();
        }
        batchConsumer.accept(batch);
        // If a flush is in progress, the batch is submitted by the first call after that flush is complete
        if (batch.isFull() && !isFlushInProgress()) {
            submitFlush();
        }
        return null;
    }

    // Must be called with the lock held
    private boolean isFlushInProgress() {
        return (flushingFuture != null) && !flushingFuture.isDone();
    }

    // Fails the flush if the current thread is interrupted. The interrupted flag is left set
    private static void throwIfInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("Interrupted while flushing");
        }
    }

    // Must be called with the lock held
    private void throwIfNotInitializedOrFinished() {
        if (batch == null) {
            throw new IllegalStateException("Flusher is not initialized or already finished");
        }
    }

    // Must be called with the lock held, and only while the batch isn't null, i.e. not after finish() has shut
    // down the executor, so a shut down executor means cancelled. CancellationException is only the cause: if thrown
    // directly, it would make futures and fork join tasks of the callers look
    // cancelled rather than failed
    private void throwIfCancelledOrFailed() {
        if (executor.isShutdown()) {
            throw new IllegalStateException("Flusher is cancelled", new CancellationException("Flusher is cancelled"));
        }
        if ((flushingFuture != null) && (flushingFuture.state() == Future.State.FAILED)) {
            throw new IllegalStateException("Flush failed", flushingFuture.exceptionNow());
        }
    }

    // Waits till the given flush is complete. Throws the same way as throwIfCancelledOrFailed(), if the flush
    // has failed or is cancelled, and if the current thread is interrupted
    private static void awaitFlush(@NonNull final Future<?> flush) {
        try {
            flush.get();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for a flush", e);
        } catch (final CancellationException e) {
            throw new IllegalStateException("Flusher is cancelled", e);
        } catch (final ExecutionException e) {
            throw new IllegalStateException("Flush failed", e.getCause());
        }
    }

    // Takes the current batch and submits a flush for it. Must be called with the lock held, when no flush
    // is in progress, and this flusher isn't cancelled or failed
    private void submitFlush() {
        final Batch toFlush = batch;
        flushingFuture = executor.submit(() -> {
            try {
                flush(toFlush);
            } catch (final RuntimeException | Error e) {
                logIfCancelled(e);
                throw e;
            }
        });
        // Replaced only once submitted, so the batch isn't lost, if submit() throws
        batch = new Batch();
        // The full batch is taken, so the stall, if any, is over
        stopBackpressureTimer();
    }

    // Called by a failed flush. If this flusher is cancelled, the failure isn't reported to the caller, so it's
    // logged here. The failed flush is still the last submitted one, since no new flushes are submitted while
    // it's in progress, or after it's cancelled
    private synchronized void logIfCancelled(@NonNull final Throwable failure) {
        if (flushingFuture.isCancelled()) {
            logger.warn(logMarker, "Flush failed after the flusher was cancelled", failure);
        }
    }

    // Adds the ongoing stall, if any, to the total. Must be called with the lock held
    private void stopBackpressureTimer() {
        if (backpressureSince != 0) {
            backpressureTotalMs += System.currentTimeMillis() - backpressureSince;
            backpressureSince = 0;
        }
    }

    // Logs the total time producers were blocked on backpressure, in finish() or cancel(). If producers are
    // still blocked, e.g. when cancelled, the ongoing stall is included. Must be called with the lock held
    private void logBackpressureTotal() {
        stopBackpressureTimer();
        logger.info(logMarker, "Producers were blocked on backpressure for {} ms in total", backpressureTotalMs);
    }

    // Since flushes may take quite some time, this method is always called outside synchronized blocks
    private void flush(@NonNull final Batch toFlush) {
        throwIfInterrupted();
        logger.info(
                logMarker,
                "Flush: {} updated hash chunks, {} updated leaves, {} deleted leaves",
                toFlush.hashChunks.size(),
                toFlush.leaves.size(),
                toFlush.deletedLeaves.size());
        // flush it down
        final long start = System.currentTimeMillis();
        try {
            dataSource.saveRecords(
                    firstLeafPath,
                    lastLeafPath,
                    toFlush.hashChunks.stream(),
                    toFlush.leaves.stream(),
                    toFlush.deletedLeaves.stream(),
                    reconnectContext);
            // If interrupted, saveRecords() restores the interrupted flag and returns normally,
            // possibly before all data is written. This must not be treated as a success
            throwIfInterrupted();
            final long end = System.currentTimeMillis();
            statistics.recordFlush(end - start);
            logger.debug(logMarker, "Flushed in {} ms", end - start);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    // Test only
    boolean awaitFlusherTermination(final long timeout, @NonNull final TimeUnit unit) throws InterruptedException {
        return executor.awaitTermination(timeout, unit);
    }
}
