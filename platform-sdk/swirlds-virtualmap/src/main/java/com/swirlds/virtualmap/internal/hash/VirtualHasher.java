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

package com.swirlds.virtualmap.internal.hash;

import static com.swirlds.common.threading.manager.ThreadManagerFactory.getStaticThreadManager;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.virtualmap.internal.Path.INVALID_PATH;
import static com.swirlds.virtualmap.internal.Path.ROOT_PATH;
import static com.swirlds.virtualmap.internal.Path.getIndexInRank;
import static com.swirlds.virtualmap.internal.Path.getParentPath;
import static com.swirlds.virtualmap.internal.Path.getRank;
import static com.swirlds.virtualmap.internal.Path.getSiblingPath;

import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.HashBuilder;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualMap;
import com.swirlds.virtualmap.VirtualMapSettingsFactory;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualInternalRecord;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.internal.Path;
import java.util.Iterator;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongFunction;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Responsible for hashing virtual merkle trees. This class is designed to work both for normal
 * hashing use cases, and also for hashing during reconnect.
 *
 * @param <K>
 * 		The {@link VirtualKey} type
 * @param <V>
 * 		The {@link VirtualValue} type
 */
public final class VirtualHasher<K extends VirtualKey<? super K>, V extends VirtualValue> {
    /**
     * Use this for all logging, as controlled by the optional data/log4j2.xml file
     */
    private static final Logger logger = LogManager.getLogger(VirtualHasher.class);

    /**
     * The number of threads to use when hashing. Can either be supplied by a system property, or
     * will compute a default based on "percentHashThreads".
     */
    private static final int HASHING_THREAD_COUNT =
            VirtualMapSettingsFactory.get().getNumHashThreads();

    /**
     * A thread pool for processing hashing work. A single executor service is shared across all {@link VirtualMap}
     * instances. This is an unbounded cached thread pool. If more than one virtual merkle tree is being hashed
     * concurrently, they will spawn threads as needed. A thread that is unused will eventually be purged from the
     * pool. This approach was used to avoid more complicated thread pool semantics, however, it is anticipated
     * that in the future we will want to have a fixed thread pool to better manage compute resources.
     */
    private static final ExecutorService HASHING_POOL =
            Executors.newCachedThreadPool(getStaticThreadManager().newThreadConfiguration()
                    .setThreadGroup(new ThreadGroup("virtual-map-hashers"))
                    .setComponent("virtual-map")
                    .setThreadName("hasher")
                    .setExceptionHandler(
                            (t, ex) -> logger.error(EXCEPTION.getMarker(), "Uncaught exception during hashing", ex))
                    .buildFactory());

    /**
     * This thread-local gets a HashBuilder that can be used for hashing on a per-thread basis.
     */
    private static final ThreadLocal<HashBuilder> HASH_BUILDER_THREAD_LOCAL =
            ThreadLocal.withInitial(() -> new HashBuilder(Cryptography.DEFAULT_DIGEST_TYPE));

    /**
     * The working queue or the pending queue. Sometimes it is used as one, sometimes as the other.
     * It is kept and passed between {@link VirtualMap} copies, with the ASSUMPTION
     * that only a single copy of the {@link VirtualMap} is being hashed at a time (which should be true).
     * The reason for keeping the queue and passing it around is to cut down on garbage and temporary allocations.
     */
    private final ArrayHashingQueue<K, V> queue1;

    /**
     * The working queue or the pending queue. Sometimes it is used as one, sometimes as the other.
     * It is kept and passed between {@link VirtualMap} copies, with the ASSUMPTION
     * that only a single copy of the {@link VirtualMap} is being hashed at a time (which should be true).
     * The reason for keeping the queue and passing it around is to cut down on garbage and temporary allocations.
     */
    private final ArrayHashingQueue<K, V> queue2;

    /**
     * The max stop queue. It is kept and passed between {@link VirtualMap} copies, with the ASSUMPTION
     * that only a single copy of the {@link VirtualMap} is being hashed at a time (which should be true).
     * The reason for keeping the queue and passing it around is to cut down on garbage and temporary allocations.
     * <p>
     * There are two stop queues. One is used for jobs that are ultimately the result of processing starting
     * from the max rank, and the other is for jobs that are ultimately the result of processing starting from
     * the min rank.
     */
    private final ArrayHashingQueue<K, V> maxRankStopQueue;

    /**
     * The min stop queue. It is kept and passed between {@link VirtualMap} copies, with the ASSUMPTION
     * that only a single copy of the {@link VirtualMap} is being hashed at a time (which should be true).
     * The reason for keeping the queue and passing it around is to cut down on garbage and temporary allocations.
     * <p>
     * There are two stop queues. One is used for jobs that are ultimately the result of processing starting
     * from the max rank, and the other is for jobs that are ultimately the result of processing starting from
     * the min rank.
     */
    private final ArrayHashingQueue<K, V> minRankStopQueue;

    /**
     * The last queue. It is kept and passed between {@link VirtualMap} copies, with the ASSUMPTION
     * that only a single copy of the {@link VirtualMap} is being hashed at a time (which should be true).
     * The reason for keeping the queue and passing it around is to cut down on garbage and temporary allocations.
     */
    private final ArrayHashingQueue<K, V> lastQueue;

    /**
     * Tracks if this virtual hasher has been shut down. If true (indicating that the hasher
     * has been intentionally shut down), then don't log/throw if the rug is pulled from
     * underneath the hashing threads.
     */
    private AtomicBoolean shutdown = new AtomicBoolean(false);

    /**
     * Create a new {@link VirtualHasher}. There should be one {@link VirtualHasher} shared across all copies
     * of a {@link VirtualMap} "family".
     */
    public VirtualHasher() {
        // These queues are used for the "workingQueue" (wq), "pendingQueue" (pq), "stopQueue" (sq),
        // and "lastQueue" (lq). Which queue is which changes during execution.
        this.queue1 = new ArrayHashingQueue<>();
        this.queue2 = new ArrayHashingQueue<>();
        this.maxRankStopQueue = new ArrayHashingQueue<>();
        this.lastQueue = new ArrayHashingQueue<>();
        this.minRankStopQueue = new ArrayHashingQueue<>();
    }

    /**
     * Indicate to the virtual hasher that it has been shut down. This method does not interrupt threads, but
     * it indicates to threads that an interrupt may happen, and that the interrupt should not be treated as
     * an error.
     */
    public void shutdown() {
        shutdown.set(true);
    }

    /**
     * Hash the given dirty leaves and the minimal subset of the tree necessary to produce a single root hash.
     * The root hash is returned.
     *
     * @param leafReader
     * 		Return a {@link VirtualLeafRecord} by path. Used when this method needs to look up clean leaves.
     * @param internalReader
     * 		Return a {@link VirtualInternalRecord} by path. Used when this method needs to look up clean internals.
     * @param sortedDirtyLeaves
     * 		A stream of dirty leaves sorted in <strong>ASCENDING PATH ORDER</strong>, such that path
     * 		1234 comes before 1235. If null or empty, a null hash result is returned.
     * @param firstLeafPath
     * 		The firstLeafPath of the tree that is being hashed. If &lt; 1, then a null hash result is returned.
     * 		No leaf in {@code sortedDirtyLeaves} may have a path less than {@code firstLeafPath}.
     * @param lastLeafPath
     * 		The lastLeafPath of the tree that is being hashed. If &lt; 1, then a null hash result is returned.
     * 		No leaf in {@code sortedDirtyLeaves} may have a path greater than {@code lastLeafPath}.
     * @return The hash of the root of the tree
     */
    public Hash hash(
            final LongFunction<VirtualLeafRecord<K, V>> leafReader,
            final LongFunction<VirtualInternalRecord> internalReader,
            Iterator<VirtualLeafRecord<K, V>> sortedDirtyLeaves,
            final long firstLeafPath,
            final long lastLeafPath) {
        return hash(leafReader, internalReader, sortedDirtyLeaves, firstLeafPath, lastLeafPath, null);
    }

    /**
     * Hash the given dirty leaves and the minimal subset of the tree necessary to produce a single root hash.
     * The root hash is returned.
     *
     * @param leafReader
     * 		Return a {@link VirtualLeafRecord} by path. Used when this method needs to look up clean leaves.
     * @param internalReader
     * 		Return a {@link VirtualInternalRecord} by path. Used when this method needs to look up clean internals.
     * @param sortedDirtyLeaves
     * 		A stream of dirty leaves sorted in <strong>ASCENDING PATH ORDER</strong>, such that path
     * 		1234 comes before 1235. If null or empty, a null hash result is returned.
     * @param firstLeafPath
     * 		The firstLeafPath of the tree that is being hashed. If &lt; 1, then a null hash result is returned.
     * 		No leaf in {@code sortedDirtyLeaves} may have a path less than {@code firstLeafPath}.
     * @param lastLeafPath
     * 		The lastLeafPath of the tree that is being hashed. If &lt; 1, then a null hash result is returned.
     * 		No leaf in {@code sortedDirtyLeaves} may have a path greater than {@code lastLeafPath}.
     * @param listener
     * 		A {@link VirtualHashListener} that will receive notification of all hashing events. Can be null.
     * @return The hash of the root of the tree
     */
    public Hash hash(
            final LongFunction<VirtualLeafRecord<K, V>> leafReader,
            final LongFunction<VirtualInternalRecord> internalReader,
            final Iterator<VirtualLeafRecord<K, V>> sortedDirtyLeaves,
            final long firstLeafPath,
            final long lastLeafPath,
            VirtualHashListener<K, V> listener) {

        // Hashing algo v5. This version of the hashing algorithm is designed to optimally process
        // a stream of dirty leaves sorted in **ASCENDING PATH ORDER**. During "reconnect", dirty leaves
        // are streamed to the learner in ascending path order. By hashing them in this order, we can
        // process them as they are streamed. In the previous algorithm, we processed leaves in descending
        // path order, which would have required writing all dirty leaves to disk and then reading them back
        // out again, and keeping track of which were dirty in an in-memory bit array. By processing them in
        // ascending path order, we have no need to keep a history of which nodes are dirty, and we do not have
        // to read the data back from disk. When reconnecting large state (1 billion leaves for example), this
        // should result in a major improvement.
        //
        // Unfortunately, this algorithm is significantly more complicated than the previous version, which
        // was itself fairly clever code! There are two problems that we had to solve for. First, processing
        // in ascending path order is problematic because the first leaves we process may require hashes from
        // the last leaves we process, because the first leaves may be siblings to internal nodes that are parents
        // of the last leaves. Second, this algorithm is designed to hash a billion+ leaf tree, whereas the
        // previous algorithm only worked with a set of dirty leaves that could fit into memory.
        //
        // The essential thing about this algorithm is that we subdivide the tree and hash sub-trees of manageable
        // size, one at a time. For this to work, the segment needs to be sized as a power of 2 such that for the
        // max rank (the rank of the lastLeafPath) it evenly subdivides the theoretical max number of elements
        // for that rank.

        // If the first or last leaf path are invalid, then there is nothing to hash.
        if (firstLeafPath < 1 || lastLeafPath < 1) {
            return null;
        }

        // If the stream is empty, then there is nothing to hash.
        final PeekIterator<VirtualLeafRecord<K, V>> itr = new PeekIterator<>(sortedDirtyLeaves);
        if (!itr.hasNext()) {
            return null;
        }

        // We don't want to include null checks everywhere, so let the listener be NoopListener if null
        if (listener == null) {
            listener =
                    new VirtualHashListener<>() {
                        /* noop */
                    };
        }

        // Given some tree, leaves will be on the rank of the lastLeafPath, and the rank of the firstLeafPath.
        // Most often this involves two ranks. In the rare exception the number of leaves is exactly a power of
        // two, and there is only one rank involved. Each rank has a different segment size. The rank of the
        // lastLeafPath has the largest segment size, while the next rank up has a segment size that is exactly half,
        // and so on up the tree. If the segment size is less than the max number of elements in a rank, then there
        // is some rank at which the segment size is 1, and that rank may not be the same as the root rank. This rank
        // is known as the "stopRank", because our hashing algorithm must stop there until all sub-trees have
        // been hashed. It can then resume hashing from the "stopRank" up to the root rank. Another way of looking
        // at it is that we divide the tree up into sub-trees of roughly equal size, such that one sub-tree is the root
        // down to the stopRank, and each additional sub-tree has a root that is a node in the stopRank and extends
        // down to the bottom-most rank.

        // We know the paths are valid. In almost all cases, the rank of the first leaf and the rank of the
        // last leaf will be different. We need to know what rank each path are on for our setup code.
        final int minLeafRank = Path.getRank(firstLeafPath);
        final int maxLeafRank = Path.getRank(lastLeafPath);

        // Compute the stopRank with X, such that 2^X >= maxLeaves and 2^X-1 < maxLeaves, and stopRank = X / 4.
        // (It produced too many small files when we divided by 2, instead of 4.)  It appears to work just fine by
        // changing the denominator to 4, but we may need to revisit this sometime later.
        // For example, given 1 billion maxLeaves produces an X of 30 (2^30 > 1 billion, 2^29 < 1 billion)
        // and a stopRank then equals 7. It turns out, this is simply 1/4-way up the tree from maxLeafRank!
        final int stopRank = maxLeafRank >> 2;

        // As mentioned above, we sub-divide the tree into roughly equal-sized sub-trees. This was a deliberate
        // oversimplification. If we were to literally divide the tree in this way, we may have sub-trees with
        // very few dirty nodes requiring hashing, and this would be a waste of resources because few threads
        // could be involved. It is much better to dynamically size the sub-tree such that a sufficient number
        // of nodes are being handled at the same time. Because we have a binary tree, we can take advantage
        // of the properties of the binary tree to know for certain that we can hash a subset independently.
        // If I have a stopRank with 8 elements, then I know for certain that I could process any sub-tree
        // who's base level consisted of 8 elements. I know for certain that I can process any sub-tree with
        // 16 elements, or 24 elements, or 32 elements or any other multiple of 8 completely independently.
        // Thus, given the number of elements in the stopRank, I can determine the size by which I can "segment"
        // the tree into independent sub-trees. I can then accumulate all dirty nodes within the first segment.
        // If I have accumulated less than the "segment" size of dirty nodes, I can go ahead and accumulate
        // another segment-worth of nodes. This means, that if my segment size were 8, then I would try to
        // accumulate at least 8 dirty nodes, but up to 15 dirty nodes. This may involve a single segment,
        // or as many as all segments in a rank.
        //
        // If I have a rank where every leaf is dirty, then each sub-tree is exactly one segment-size in width,
        // and I will process one segment-size worth of leaf nodes at a time. If the rank has a single
        // dirty leaf, then I will process the entire rank in one go. If some segment in the rank has
        // (segmentSize - 1) dirty leaves and a subsequent segment with segmentSize dirty leaves, then
        // I will process ((segmentSize * 2) - 1) dirty nodes in that pass. Each pass may have a different
        // number of nodes being processed, but all will fall somewhere between 1 and ((segmentSize * 2) - 1)
        // in count.
        //
        // If there are two ranks involved (firstLeafPath is on a rank and lastLeafPath is on the next rank)
        // and if the first dirty leaf lies within the segment that includes the firstLeafPath, and if the
        // firstLeafPath is not positioned at the beginning of that segment, then two or more leaves at the
        // end of the leaves on the last rank (including the lastLeafPath) must be hashed before the dirty
        // leaves in this first segment are processed, otherwise an incorrect hash will be computed. While
        // this may seem like a corner case, it is actually extremely common. To handle this case, when we
        // detect it, we store off the first segment-worth of dirty leaves (literally, the dirty leaves in
        // this specific segment only) into the "lastQueue", because it is saved until last.
        //
        // We then move on, hashing all other leaves and internal nodes like normal, accumulating all nodes
        // within a segment into the "workingQueue" (also known as 'wq') until it has more nodes than
        // segmentSize, and then hand it off to get hashed, and then continue until we get towards the end
        // of the last rank of leaves.
        //
        // If lastQueue is not empty, then there are some number of "reserved" nodes near the end of the last rank
        // that belong to the same hashing round as those in the lastQueue. Each internal node between the start
        // of the segment and the firstLeafPath corresponds to exactly two nodes in the last rank. For example,
        // if the segment started on path 51, and the firstLeafPath was 52, then there would be exactly two
        // nodes in the last rank that belonged to this round (lastLeafPath and lastLeafPath - 1). We would
        // make sure, while accumulating dirty nodes into the `wq` for a hashing round, that we do not include
        // any of the reserved nodes -- they must form their own complete hashing round.

        // Figure out the "segment" size to use for the two ranks that may contain leaves. Note that it is possible
        // that minLeafRank and maxLeafRank are the same, and therefore it is possible that minRankSegmentSize
        // and maxRankSegmentSize are also both equivalent.
        final int minRankSegmentSize = 1 << (minLeafRank - stopRank);
        final int maxRankSegmentSize = 1 << (maxLeafRank - stopRank);

        // For this algorithm, we maximally size our work and pending queues to be the max rank
        // segment size * 2 which allows us to read the entire next segment into the buffer without
        // having to do read-ahead and array copies or worry about buffer overflow.
        final int maxQueueSize = maxRankSegmentSize * 2;
        queue1.ensureCapacity(maxQueueSize);
        queue2.ensureCapacity(maxQueueSize);
        // The stop queues only need to be the same size as the maxRankSegmentSize, not double
        // (it may be that either stop queue needs to be large enough for the whole rank).
        // The "lastQueue" is only ever populated with leaves on the minLeafRank, so it only
        // needs to be large enough for minRankSegmentSize. This is one additional wrinkle. It is
        // critical that the stop queue is ordered properly in ascending path order. The problem
        // is that we process the minLeafRank first, and then the maxLeafRank. So we actually have
        // *TWO* stop queues! One is for the stop-queue level nodes for the minLeafRank, and one is
        // for the stop-queue level nodes for the maxLeafRank. When we process the stop level, we
        // actually combine the two queues together using a CompoundHashingQueue to avoid any
        // array or buffer copies.
        minRankStopQueue.ensureCapacity(maxRankSegmentSize);
        maxRankStopQueue.ensureCapacity(maxRankSegmentSize);
        lastQueue.ensureCapacity(minRankSegmentSize);

        // Compute the distance between the segment boundary and the firstLeafPath's index within the rank.
        final long firstLeafIndexInRank = getIndexInRank(firstLeafPath);
        final long firstLeafOffsetWithinSegment = firstLeafIndexInRank % minRankSegmentSize;

        // If there are two ranks to process, and *IF* the firstLeafPath does not line up with the start
        // of the segment, then read off every leaf we find that is in the minLeafRank AND within
        // the initial segment (the same segment that the firstLeafPath lies within), and save them off in a buffer
        // for later use. If leaves are in a single rank, there is no need to do this.
        if (minLeafRank != maxLeafRank && firstLeafOffsetWithinSegment > 0) {
            readLeavesInSegment(itr, lastQueue, firstLeafPath + (minRankSegmentSize - firstLeafOffsetWithinSegment));
        }

        // Compute the number of leaves to reserve on the very last rank for inclusion with the first leaves
        final long reservedLastLeafCount = lastQueue.size() == 0 ? 0 : (firstLeafOffsetWithinSegment * 2);
        assert reservedLastLeafCount > 0 ? (lastQueue.size() > 0) : (lastQueue.size() == 0)
                : "Improper computation of reservedLastLeafCount";

        // Let the listener know we have started hashing.
        listener.onHashingStarted();

        // Iterate over all dirty leaves until we encounter the very last segment (the so-called
        // reserved segment).
        long lastPath = -1;
        while (itr.hasNext()) {
            final VirtualLeafRecord<K, V> next = itr.peek();
            final long path = next.getPath();
            final int rank = getRank(path);

            // SANITY CHECK: Fail fast if this condition does not hold
            if (path < lastPath) {
                throw new IllegalStateException("The paths in the iterator must be strictly increasing! " + "lastPath="
                        + lastPath + ", path=" + path);
            }
            lastPath = path;

            // The path must always be within this range. We can use an assertion here because,
            // unless there is some bug in our code, this cannot happen.
            assert path >= firstLeafPath && path <= lastLeafPath
                    : "Invalid path lies outside the leaf path range " + path;

            // Break out of the loop if the next leaf to process is in the reserved space
            if (rank == maxLeafRank && path >= lastLeafPath - reservedLastLeafCount) {
                break;
            }

            // Depending on the rank we're processing, we need to know the segment size.
            final long segmentSize = rank == minLeafRank ? minRankSegmentSize : maxRankSegmentSize;
            // Either eof is the end of the rank, or the last leaf before the reserved section
            final long eofPath = Math.min((1L << (rank + 1)) - 1, lastLeafPath - reservedLastLeafCount + 1);

            // Populate the wq with leaves by accumulating them from the iterator, a segment at a time.
            final HashingQueue<K, V> wq = queue1.reset();
            accumulate(itr, wq, path - (getIndexInRank(path) % segmentSize), segmentSize, eofPath);

            // Setup and hash the subtree that we have accumulated. If I am hashing the minLeafRank,
            // then the results go into the minRankStopQueue. If I am hashing the maxLeafRank, then
            // the results go into the maxRankStopQueue.
            final HashingQueue<K, V> pq = queue2.reset();
            final HashingQueue<K, V> sq = rank == maxLeafRank ? maxRankStopQueue : minRankStopQueue;
            listener.onBatchStarted();
            hashSubTree(
                    leafReader,
                    internalReader,
                    listener,
                    wq,
                    pq,
                    null,
                    sq,
                    firstLeafPath,
                    lastLeafPath,
                    rank,
                    stopRank);
            listener.onBatchCompleted();
        }

        // If there are still remaining leaves to process (which must be in the "reserved" area),
        // or if there were some leaves put in the "lastQueue", then process them now.
        if (itr.hasNext() || lastQueue.size() > 0) {
            final HashingQueue<K, V> wq = queue1.reset();
            readLeavesInSegment(itr, wq, lastLeafPath + 1);
            final HashingQueue<K, V> pq = queue2.reset();
            listener.onBatchStarted();
            hashSubTree(
                    leafReader,
                    internalReader,
                    listener,
                    wq,
                    pq,
                    lastQueue,
                    maxRankStopQueue,
                    firstLeafPath,
                    lastLeafPath,
                    maxLeafRank,
                    stopRank);
            listener.onBatchCompleted();
        }

        // By this point we have hashed all the way from the leaves to the stopLevel, and the results
        // are in the maxRankStopQueue and minRankStopQueue. Now hash from the stopLevel to the root.
        // We use a CompoundHashingQueue to combine the two stop queues to avoid any array copies.
        listener.onBatchStarted();
        hashSubTree(
                leafReader,
                internalReader,
                listener,
                new CompoundHashingQueue<>(maxRankStopQueue, minRankStopQueue),
                queue1.reset(),
                null,
                queue2.reset(),
                firstLeafPath,
                lastLeafPath,
                stopRank,
                0);

        // If everything worked correctly, there is a single HashJob in queue2 (which we used as the
        // "stopQueue" -- i.e. the accumulator for the root level). We can just get this root job, hash
        // it, and return the hash.
        assert queue2.size() == 1
                : "There must only be a single hash job in the root queue!! Current size = " + queue2.size();
        final HashJob<K, V> rootJob = queue2.get(0);
        rootJob.hash(HASH_BUILDER_THREAD_LOCAL.get());
        listener.onRankStarted();
        listener.onInternalHashed(rootJob.getInternal());
        listener.onRankCompleted();
        listener.onBatchCompleted();
        listener.onHashingCompleted();
        return rootJob.getHash();
    }

    /**
     * Hashes a sub-tree using multiple threads.
     *
     * @param leafReader
     * 		Return a {@link VirtualLeafRecord} by path. Used when this method needs to look up clean leaves.
     * @param internalReader
     * 		Return a {@link VirtualInternalRecord} by path. Used when this method needs to look up clean internals.
     * @param listener
     * 		A {@link VirtualHashListener} that will receive notification of all hashing events. Cannot be null.
     * @param wq
     * 		The working queue. Cannot be null.
     * @param pq
     * 		The pending queue. Cannot be null.
     * @param lq
     * 		The last queue. Can be null.
     * @param sq
     * 		The stop queue. Cannot be null.
     * @param firstLeafPath
     * 		The firstLeafPath.
     * @param lastLeafPath
     * 		The lastLeafPath.
     * @param startRank
     * 		The startRank. Can be the same as stopRank. Must be greater than or equal to zero.
     * @param stopRank
     * 		The stopRank. Can be the same as the startRank. Must be greater than or equal to zero.
     */
    private void hashSubTree(
            final LongFunction<VirtualLeafRecord<K, V>> leafReader,
            final LongFunction<VirtualInternalRecord> internalReader,
            final VirtualHashListener<K, V> listener,
            HashingQueue<K, V> wq,
            HashingQueue<K, V> pq,
            HashingQueue<K, V> lq,
            final HashingQueue<K, V> sq,
            final long firstLeafPath,
            final long lastLeafPath,
            final int startRank,
            final int stopRank) {

        // Unless we have a bug, this will always hold true
        assert wq != null && pq != null && sq != null : "Unexpected null for pq or wq or sq";
        assert startRank >= 0 : "startRank was negative!";
        assert stopRank >= 0 : "stopRank was negative!";
        assert listener != null : "Listener cannot be null in hashSubTree";

        Objects.requireNonNull(leafReader, "leaf reader is not permitted to be null");
        Objects.requireNonNull(internalReader, "internal reader is not permitted to be null");

        // We maintain two different HashQueues, one for the current list of HashJobs in a single rank
        // (sorted in ascending order by path), and one for the next list of HashJobs for the next rank
        // closer to stopRank (also sorted in ascending order by path). After processing all jobs in
        // wq and placing new jobs in pq, we switch the role of pq and wq and do it again. We continue
        // in this way up the tree until we have hashed the rank just "below" stopRank and stored in
        // the pq one or more jobs for the stopRank.
        //
        // For each iteration, we spawn multiple threads (limited by HASHING_THREAD_COUNT). Each thread
        // independently walks over the current "wq". Within the queue are all the dirty nodes
        // at that rank, in descending order by path. When a node is processed, it needs its sibling as
        // well so that it can give its hash and its sibling's hash to the parent node (which has a HashJob
        // created for it and placed into the pending queue at the right location). Sometimes only a single
        // node of the two is dirty, and sometimes both siblings are dirty. We call this a "unit". Either it
        // is a unit of 1 (the dirty node and a clean sibling) or a unit of 2 (the dirty node and its dirty
        // sibling).
        //
        // Each thread, then walks over the workQueue looking for the units that belong to it. This is
        // done efficiently in a lock-free manner by giving each unit an index and assigning the unit
        // to the thread whose ordinal can be found via modulo. Thus, each thread has certain assigned
        // units to process, and they can all process them completely in parallel.
        //
        // Before turning the threads loose, we set up a CountDownLatch such that when a thread finishes
        // processing the rank it decrements the barrier and exits. The main thread waits until all
        // hashing threads have hit the barrier before swapping the pq and wq, making the
        // pq the new wq and wq the old pq and resetting the new pq back to 0 elements.
        //
        // For efficiency reasons, we do not actually clear the state from the queues. We just reset the
        // size to 0 and manage it that way. This is a little risky, and keeps some hashes in memory
        // far longer than necessary, but reduces garbage and improves overall system performance.

        // If the start and stop rank are the same, then we have nothing to hash. We just have to transfer
        // the items from the work queue to the stop queue. This only happens with rank 0 or rank 1.
        if (startRank == stopRank) {
            assert startRank == 1 || startRank == 0 : "Expected rank 0 or 1, was " + startRank;
            sq.copyFrom(wq);
            return;
        }

        // Used to hold exceptions thrown by the hashing threads.
        final Queue<Throwable> exceptions = new ConcurrentLinkedDeque<>();

        // For each rank, start threads to process the work within those threads. When the threads
        // complete, swap queues and run the next rank. Continue this until we get all the way to the end.
        // The reason we grab and use a bunch of threads for each rank and then let them complete is so that
        // if we have multiple VirtualMap instances, they can all pull from the same queue and not end up
        // having to run in sequence. However, to really implement that, we need some counter to keep track
        // of the number of threads available before we try to grab any, or we could deadlock with two
        // maps both trying to get > half of the available threads and getting stuck. Note that we DO NOT
        // process the very last rank, which only contains root. Instead, we will handle that separately.
        for (int rank = startRank; rank > stopRank; rank--) {
            final HashingQueue<K, V> workQueue = wq;
            final HashingQueue<K, V> pendingQueue = rank == stopRank + 1 ? sq : pq;

            // Compute the number of threads to use. For ranks with lots and lots of potential work,
            // we use HASHING_THREAD_COUNT. For ranks where there are always few potential jobs,
            // we don't need as many threads, so we might as well leave them available for other
            // virtual maps to use. Of course, use wq.size() instead if it is smallest.
            final int threadCount = Math.min(workQueue.size(), Math.min(HASHING_THREAD_COUNT, 1 << (rank - stopRank)));

            final boolean hasLastQueue = (lq != null && lq.size() > 0);
            assert workQueue.size() > 0 || hasLastQueue : "Work queue is empty for rank " + rank;
            assert threadCount > 0 || hasLastQueue
                    : "Thread count is zero for rank " + rank + ", max hashing threads configured to be "
                            + HASHING_THREAD_COUNT;

            // This latch is used to cause this thread to wait until all hashing threads complete their work.
            final CountDownLatch latch = new CountDownLatch(threadCount);
            final int offset = pendingQueue == sq ? sq.size() : 0;
            final int workQueueSize = workQueue.size();
            // Spawn each hashing thread
            for (int i = 0; i < threadCount; i++) {
                final int threadNum = i;
                HASHING_POOL.execute(() -> {
                    final HashBuilder hashBuilder = HASH_BUILDER_THREAD_LOCAL.get();
                    try {
                        // Each thread iterates over all "units". A unit is either a single job or two jobs if
                        // they are siblings.
                        for (int j = 0, unitIndex = 0; j < workQueueSize; j++, unitIndex++) {
                            // Get the hash job (which is always part of the unit) and the nextJob which *may* be
                            // part of the unit.
                            final HashJob<K, V> hashJob = workQueue.get(j);
                            final HashJob<K, V> nextJob = j < workQueueSize - 1 ? workQueue.get(j + 1) : null;
                            final long nodePath = hashJob.getPath();

                            // We never process the root node in background threads.
                            assert nodePath != ROOT_PATH && getRank(nodePath) != stopRank;
                            assert nodePath != INVALID_PATH;

                            // Get the sibling path. This path is also never ROOT or INVALID,
                            // because it is always for rank (stopRank + 1) or greater.
                            final long siblingPath = getSiblingPath(nodePath);
                            assert siblingPath != ROOT_PATH && getRank(nodePath) != stopRank;
                            assert siblingPath != INVALID_PATH;

                            // If both is true, then both are part of the unit. If they are part of the
                            // same unit, then we will increment "j" so that we skip it on the next
                            // iteration.
                            final boolean both = nextJob != null && nextJob.getPath() == siblingPath;
                            if (both) {
                                j++;
                            }

                            // Now see whether this thread should be handling this unit. If not, then we simply
                            // fall out of this if statement and check the next unit.
                            if (unitIndex % threadCount == threadNum) {
                                // Hash the first node
                                hashJob.hash(hashBuilder);

                                // We now need to figure out who the parent is. If the parent is not
                                // in the cache or on disk, then it means we've never seen this parent
                                // before (which can happen, for example, when the tree is expanding).
                                // In that case, we create a new internal node. When it is hashed,
                                // it will end up being saved in the cache.
                                final long parentPath = getParentPath(nodePath);
                                final VirtualInternalRecord internal = new VirtualInternalRecord(parentPath);

                                // We place the hash job that we create for the parent into the pending
                                // queue at this location. Since multiple threads are running concurrently,
                                // they all need to know where in the pendingQueue to place their results.
                                // It turns out this is trivial, since we know each unit from the work queue
                                // is in order, we also know each unit placed into the pendingQueue will be
                                // in order. So we use the unit index + the offset.
                                final int pendingQueueIndex = offset + unitIndex;

                                if (both) {
                                    // If we have both siblings, then we can hash the sibling and place both
                                    // hashes for both siblings into the HashJob for the internal node and
                                    // add it to the pendingQueue.
                                    nextJob.hash(hashBuilder);
                                    pendingQueue
                                            .addHashJob(pendingQueueIndex)
                                            .dirtyInternal(parentPath, internal, hashJob.getHash(), nextJob.getHash());
                                } else if (nodePath == firstLeafPath && nodePath == lastLeafPath) {
                                    // There is only one leaf, and hashJob is it! There is no sibling
                                    pendingQueue
                                            .addHashJob(pendingQueueIndex)
                                            .dirtyInternal(parentPath, internal, hashJob.getHash(), null);
                                } else if (siblingPath >= firstLeafPath) {
                                    // The sibling is *DEFINITELY* a leaf because its path is equal to
                                    // or greater than the first leaf path. But, since the sibling wasn't
                                    // part of the unit, I know it was clean. So we need to load the hash for it.
                                    // I know the hash MUST exist, because either it was dirty in a previous
                                    // round and is stored in the cache, or it was written to disk. Otherwise, if
                                    // it were dirty this round, it would have been in the work queue and part
                                    // of this unit.

                                    final VirtualLeafRecord<K, V> sibling = leafReader.apply(siblingPath);

                                    if (sibling == null) {
                                        throw new NullPointerException("Failed to find leaf for " + siblingPath
                                                + ", which is a sibling of " + nodePath);
                                    }

                                    final Hash siblingHash = sibling.getHash();

                                    if (siblingHash == null) {
                                        throw new IllegalStateException("Failed to find leaf hash for " + siblingPath
                                                + ", which is a sibling of " + nodePath);
                                    }

                                    final Hash leftHash = nodePath < siblingPath ? hashJob.getHash() : siblingHash;
                                    final Hash rightHash = nodePath < siblingPath ? siblingHash : hashJob.getHash();
                                    pendingQueue
                                            .addHashJob(pendingQueueIndex)
                                            .dirtyInternal(parentPath, internal, leftHash, rightHash);
                                } else {
                                    // The sibling *MUST* be a clean internal node. It isn't a clean leaf, or
                                    // a dirty sibling, so it must be a clean internal.
                                    final VirtualInternalRecord siblingInternal = internalReader.apply(siblingPath);
                                    assert siblingInternal != null : "Should never be able to be null";
                                    final Hash siblingHash = siblingInternal.getHash();

                                    if (siblingHash == null) {
                                        throw new IllegalStateException("Failed to find internal hash for "
                                                + siblingPath + ", which is a sibling of " + nodePath);
                                    }

                                    final Hash leftHash = nodePath < siblingPath ? hashJob.getHash() : siblingHash;
                                    final Hash rightHash = nodePath < siblingPath ? siblingHash : hashJob.getHash();
                                    pendingQueue
                                            .addHashJob(pendingQueueIndex)
                                            .dirtyInternal(parentPath, internal, leftHash, rightHash);
                                }
                            }
                        }
                    } catch (final Throwable exception) {
                        exceptions.add(exception);
                    } finally {
                        // The thread has finished iterating over the work queue, so we must count down
                        // at this latch. This is in the "finally" block so that we DO NOT under any
                        // circumstance fail to do this, otherwise we'll hang the system.
                        latch.countDown();
                    }
                });
            }

            // This thread must wait for all hashing threads to finish before we swap the pending and work
            // queues and start hashing the next rank.
            try {
                latch.await();
            } catch (final InterruptedException ex) {
                if (!shutdown.get()) {
                    logger.error(EXCEPTION.getMarker(), "Failed to wait for all hashing threads", ex);
                }
                Thread.currentThread().interrupt();
            }

            // If there were exceptions in on any of the threads then we need to rethrow them.
            if (!exceptions.isEmpty()) {
                if (shutdown.get()) {
                    // During a shutdown the rug is pulled out from underneath the hashing threads.
                    // No need to log/throw anything in this condition.
                    return;
                }
                final RuntimeException exception =
                        new RuntimeException("exception encountered while hashing virtual tree", exceptions.remove());
                for (final Throwable t : exceptions) {
                    exception.addSuppressed(t);
                }
                throw exception;
            }

            final int pendingQueueSize = pendingQueue.size();
            final int maximumPendingQueueSize = 1 << rank;
            assert (pendingQueueSize > 0 || hasLastQueue) && pendingQueueSize <= maximumPendingQueueSize
                    : "Pending queue has an invalid size of " + pendingQueueSize + " at rank " + rank;

            // Save everything in the wq
            listener.onRankStarted();
            wq.stream().forEach(j -> {
                final VirtualLeafRecord<K, V> leaf = j.getLeaf();
                if (leaf != null) {
                    listener.onLeafHashed(leaf);
                } else {
                    listener.onInternalHashed(j.getInternal());
                }
            });
            listener.onRankCompleted();

            // Swap the work & pending queues
            final HashingQueue<K, V> tmp = wq;
            wq = pq;
            pq = tmp;

            if (lq != null) {
                final HashingQueue<K, V> q = wq;
                lq.stream().forEach(job -> q.appendHashJob().dirtyLeaf(job.getPath(), job.getLeaf()));
                lq = null;
            }

            // Reset the pending queue and now initialOffset will be zero for all subsequent ranks.
            pq.reset();
        }
    }

    /**
     * Read all leaves from the given iterator that are in the given rank and segment and add them to the given buffer.
     *
     * @param itr
     * 		The iterator to read from. Cannot be null. May be at the end.
     * @param queue
     * 		The queue to write leaves into. The queue <strong>will be</strong> reset.
     * @param eofPath
     * 		The path that is one place larger than the last valid path to read to.
     * 		This could be the end of a rank, or the end of a specific segment.
     */
    private void readLeavesInSegment(
            final PeekIterator<VirtualLeafRecord<K, V>> itr, final HashingQueue<K, V> queue, final long eofPath) {
        // Iterate either until we run out of dirty leaves, or we encounter a leaf that is in the wrong rank or segment
        while (itr.hasNext()) {
            // Peek at the next leaf, but don't pull it off until we determine that it belongs in this buffer
            final VirtualLeafRecord<?, ?> nextLeaf = itr.peek();
            final long path = nextLeaf.getPath();
            if (path < eofPath) {
                // We know that this leaf belongs in the buffer, so we can go ahead and read it off now.
                queue.appendHashJob().dirtyLeaf(path, itr.next());
            } else {
                // We have encountered a leaf that is in the wrong segment.
                break;
            }
        }
    }

    private void accumulate(
            final PeekIterator<VirtualLeafRecord<K, V>> itr,
            final HashingQueue<K, V> queue,
            long segmentStart,
            final long segmentSize,
            final long eofPath) {
        // While we have not yet read our "preferred" number of items, read off a segment into the queue.
        while (queue.size() < segmentSize) {
            readLeavesInSegment(itr, queue, Math.min(segmentStart + segmentSize, eofPath));
            // If we have read as much as is available to read, then bail.
            if (!itr.hasNext() || itr.peek().getPath() >= eofPath) {
                return;
            } else {
                segmentStart += segmentSize;
            }
        }
    }

    public Hash emptyRootHash() {
        final var hashJob = new HashJob<K, V>();
        hashJob.dirtyInternal(ROOT_PATH, new VirtualInternalRecord(0), null, null);
        hashJob.hash(new HashBuilder(Cryptography.DEFAULT_DIGEST_TYPE));
        return hashJob.getHash();
    }
}
