// SPDX-License-Identifier: Apache-2.0
package com.swirlds.virtualmap.internal;

import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
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
/// threads (producers). Producers only add data to the current batch. Due batches are flushed on a
/// single-threaded executor, created in the constructor, one at a time, in the order they are filled.
/// The final flush runs in [#finish()] in the calling thread, after the executor is shut down.
///
/// [#init(long, long)] must be called before any producers start appending changes, and [#finish()] must be
/// called in the end, after all producers are done. If the job is aborted instead, [#cancel()] must be called,
/// possibly even before [#init(long, long)]. Either of them shuts down the executor.
///
/// Memory is bounded by backpressure: once the current batch is due for a flush, producers block until the
/// flush task takes it, which happens as soon as the previous flush is complete. So at most about two
/// batches are kept in memory, one being flushed and one being filled. Producers block in [Future#get()]
/// of the running flush task. It parks the thread without `ForkJoinPool.managedBlock()`, so that
/// ForkJoinPool workers don't spawn compensation threads, and it never runs other tasks.
///
/// Deadlock freedom relies on producers waiting for flush tasks only, while flush tasks never wait for
/// producers. Flushes don't run on producer threads, so a producer that is a ForkJoinPool worker can't be
/// made to run another producer task, while it waits inside a flush (FJ helping), and then block on the
/// flush it runs itself. The final flush in [#finish()] may run on such a worker, but it's safe, since all
/// producers are done by then. The data source must never call back into this flusher.
///
/// If a flush fails, the failure is sticky: no new flushes are started, producers blocked on backpressure are
/// released, and all subsequent calls, including [#finish()], throw an [IllegalStateException] with the
/// failure as the cause. Data in the failed batch and in the batches after it is lost, so the caller must
/// treat it as a failure of the whole job.
///
/// If the job that feeds this flusher fails or times out, it must call [#cancel()]. Producers blocked on
/// backpressure are released, and all subsequent calls throw an [IllegalStateException] with a
/// [CancellationException] as the cause. A flush that is already running isn't interrupted, but no new
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

    // Runs all flushes, except the final one. Its thread is only started on the first submitted flush
    private final ExecutorService executor;

    // The batch that is currently being filled. Null before init() and after finish(). Synchronized on this
    private Batch batch;

    // The flush task that is running or queued, or null if no flush is in progress. While the current batch
    // is due, it's never null, unless cancelled or failed. Synchronized on this
    private Future<?> flushTask;

    // Set by cancel(). Once set, all subsequent calls fail. Synchronized on this
    private boolean cancelled = false;

    // The first flush failure. Once set, all subsequent calls fail. Synchronized on this
    private Throwable failure = null;

    // When producers started blocking on backpressure, or 0 if none are blocked. Synchronized on this
    private long backpressureStart = 0;

    // Total time producers were blocked on backpressure, logged in finish() or cancel(). A long total may
    // indicate a slow disk, and it may make reconnect fail, since a blocked learner stops reading from the
    // teacher, which times out. Synchronized on this
    private long backpressureMs = 0;

    // Passed to the data source with every flush, see VirtualDataSource.saveRecords()
    private final boolean reconnectContext;

    // Data accumulated for a single flush
    private static final class Batch {
        final List<VirtualHashChunk> hashChunks = new ArrayList<>();
        final List<VirtualLeafBytes> leaves = new ArrayList<>();
        final List<VirtualLeafBytes> deletedLeaves = new ArrayList<>();
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
    /// If the current batch is already due for a flush, this method blocks till a flush task takes it,
    /// which happens once the running flush, if any, is complete. Never runs a flush in the current thread.
    ///
    /// @param chunk the hash chunk to save
    /// @throws IllegalStateException if this flusher is cancelled, if a flush has failed, or if the current
    ///     thread is interrupted while blocked. The interrupted flag is left set
    public void updateHashChunk(@NonNull final VirtualHashChunk chunk) {
        addToBatch(batch -> batch.hashChunks.add(chunk));
    }

    /// Adds an updated leaf to the current batch. May be called from multiple threads. Blocks the same way
    /// as [#updateHashChunk(VirtualHashChunk)].
    ///
    /// @param leaf the leaf to save
    /// @throws IllegalStateException if this flusher is cancelled, if a flush has failed, or if the current
    ///     thread is interrupted while blocked. The interrupted flag is left set
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
    /// @throws IllegalStateException if this flusher is cancelled, if a flush has failed, or if the current
    ///     thread is interrupted while blocked. The interrupted flag is left set
    public void deleteLeaf(final VirtualLeafBytes<?> leaf) {
        addToBatch(batch -> batch.deletedLeaves.add(leaf));
    }

    /// Cancels this flusher. Threads blocked on backpressure are released, and all subsequent
    /// calls to [#updateHashChunk(VirtualHashChunk)], [#updateLeaf(VirtualLeafBytes)],
    /// [#deleteLeaf(VirtualLeafBytes)], and [#finish()] throw an [IllegalStateException] with a
    /// [CancellationException] as the cause. A flush that is already running isn't interrupted, but no new
    /// flushes are started, and the executor thread exits once the running flush is complete. May be called
    /// multiple times, from any thread, at any moment, including before [#init(long, long)]. Never waits for
    /// a running flush. If this flusher is initialized and not finished yet, the total time producers were
    /// blocked on backpressure is logged.
    ///
    /// This method only affects calls into this flusher. It doesn't stop producers themselves, this is the
    /// caller's responsibility.
    public synchronized void cancel() {
        cancelled = true;
        if (flushTask != null) {
            // Releases threads blocked in get(). The running flush, if any, isn't interrupted
            flushTask.cancel(false);
        }
        executor.shutdown();
        // Not initialized or already finished, nothing to log
        if (batch != null) {
            logBackpressureTotal();
        }
    }

    /// Shuts down the executor, waiting for a flush that is already running, if any, and then flushes all
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
        synchronized (this) {
            if (batch == null) {
                throw new IllegalStateException("Flusher is not initialized or already finished");
            }
            // Log before throwIfCancelledOrFailed(), so the total is logged even if a flush has failed
            logBackpressureTotal();
            throwIfCancelledOrFailed();
            last = batch;
            batch = null;
            // Once the batch is null, the running flush task doesn't submit the next one, so the executor
            // terminates after it
            executor.shutdown();
        }
        // Wait outside of the synchronized block, so the flush task can complete, and cancel() isn't blocked.
        // Unlike Future.get(), this isn't released by cancel(), so no flush is running once it returns
        try {
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for a running flush", e);
        }
        synchronized (this) {
            throwIfCancelledOrFailed();
        }
        // Nodes / leaves lists may be empty, but a flush is still needed to make sure all stale leaves are
        // removed from the data source. The flush is run outside the synchronized block, so cancel() isn't
        // blocked till the final flush is complete
        flush(last);
    }

    private void addToBatch(final Consumer<Batch> batchConsumer) {
        // Producers never add to a batch that is already due. After every wait the state is checked again,
        // since other producers may have filled the next batch, or this flusher may have been cancelled or failed
        while (true) {
            final Future<?> running;
            synchronized (this) {
                assert batch != null : "Flusher is not initialized or already finished";
                throwIfCancelledOrFailed();
                if (!isFlushNeeded()) {
                    batchConsumer.accept(batch);
                    if (isFlushNeeded() && (flushTask == null)) {
                        submitFlush();
                    }
                    // Otherwise, the running flush task takes the batch once it's complete
                    return;
                }
                // Backpressure: the batch is due, but the running flush task hasn't taken it yet
                if (backpressureStart == 0) {
                    backpressureStart = System.currentTimeMillis();
                }
                running = flushTask;
                assert running != null : "A due batch must have a running flush";
            }
            // Waits till the given flush task is complete or cancelled (outside of the synchronized block,
            // so other producers block the same way, and cancel() isn't blocked).
            // Flush failures aren't reported here, they are recorded by the task itself,
            // so callers must check them under the lock afterwards
            try {
                running.get();
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while waiting for a flush", e);
            } catch (final CancellationException | ExecutionException e) {
                // Cancelled by cancel(). ExecutionException is never thrown, since runFlush() catches everything
            }
        }
    }

    // Must be called with the lock held. CancellationException is only the cause: if thrown directly, it would
    // make futures and fork join tasks of the callers look cancelled rather than failed
    private void throwIfCancelledOrFailed() {
        if (cancelled) {
            throw new IllegalStateException("Flusher is cancelled", new CancellationException("Flusher is cancelled"));
        }
        if (failure != null) {
            throw new IllegalStateException("Flush failed", failure);
        }
    }

    // Waits till the given flush task is complete or cancelled. Flush failures aren't reported here, they are
    // recorded by the task itself, so callers must check them under the lock afterwards
    private static void awaitFlush(@NonNull final Future<?> task) {
        try {
            task.get();
        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for a flush", e);
        } catch (final CancellationException | ExecutionException e) {
            // Cancelled by cancel(). ExecutionException is never thrown, since runFlush() catches everything
        }
    }

    // Takes the current batch and submits a flush task for it. Must be called with the lock held
    private void submitFlush() {
        final Batch toFlush = batch;
        batch = new Batch();
        flushTask = executor.submit(() -> runFlush(toFlush));
        // The due batch is taken, so the stall, if any, is over. Blocked producers are released, once the
        // previous flush task is complete
        if (backpressureStart != 0) {
            backpressureMs += System.currentTimeMillis() - backpressureStart;
            backpressureStart = 0;
        }
    }

    // Logs the total time producers were blocked on backpressure, in finish() or cancel(). If producers are
    // still blocked, e.g. when cancelled, the ongoing stall is included. Must be called with the lock held
    private void logBackpressureTotal() {
        if (backpressureStart != 0) {
            backpressureMs += System.currentTimeMillis() - backpressureStart;
            backpressureStart = 0;
        }
        logger.info(logMarker, "Producers were blocked on backpressure for {} ms in total", backpressureMs);
    }

    // Must be called with the lock held
    private boolean isFlushNeeded() {
        // If flushInterval isn't positive, all data is flushed in finish() only
        return flushInterval > 0
                && ((batch.hashChunks.size() * VirtualHashChunk.getChunkSize(hashChunkHeight) >= flushInterval)
                        || (batch.leaves.size() >= flushInterval)
                        || (batch.deletedLeaves.size() >= flushInterval));
    }

    // The flush task body. Flushes the given batch, then takes the next batch, if it became due in the
    // meantime, so it's flushed even if no producers call this flusher anymore. Never waits for producers
    private void runFlush(@NonNull final Batch toFlush) {
        try {
            // Flush outside of the synchronized block to make sure producers aren't blocked for the
            // duration of the flush, unless the next batch is also due
            flush(toFlush);
        } catch (final RuntimeException | Error e) {
            onFlusherFailure(e);
            return;
        }
        synchronized (this) {
            if (!cancelled && (batch != null) && isFlushNeeded()) {
                submitFlush();
            } else {
                flushTask = null;
            }
        }
    }

    // Records the failure and shuts down the executor. Producers blocked on backpressure are released, once
    // the failed flush task is complete
    private synchronized void onFlusherFailure(@NonNull final Throwable e) {
        failure = e;
        flushTask = null;
        executor.shutdown();
        logger.error(EXCEPTION.getMarker(), "Flush failed, no more data will be flushed", e);
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

    // Fails the flush if the current thread is interrupted. The interrupted flag is left set
    private static void throwIfInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new IllegalStateException("Interrupted while flushing");
        }
    }

    // Test only
    boolean awaitFlusherTermination(final long timeout, @NonNull final TimeUnit unit) throws InterruptedException {
        return executor.awaitTermination(timeout, unit);
    }
}
