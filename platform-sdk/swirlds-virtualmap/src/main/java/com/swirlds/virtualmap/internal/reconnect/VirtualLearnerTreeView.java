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

package com.swirlds.virtualmap.internal.reconnect;

import static com.swirlds.virtualmap.internal.Path.ROOT_PATH;
import static com.swirlds.virtualmap.internal.Path.getChildPath;
import static com.swirlds.virtualmap.internal.Path.getParentPath;
import static com.swirlds.virtualmap.internal.Path.isLeft;

import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.synchronization.internal.ExpectedLesson;
import com.swirlds.common.merkle.synchronization.utility.MerkleSynchronizationException;
import com.swirlds.common.merkle.synchronization.views.LearnerTreeView;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.threading.pool.StandardWorkGroup;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualKeySet;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import com.swirlds.virtualmap.datasource.VirtualRecord;
import com.swirlds.virtualmap.internal.RecordAccessor;
import com.swirlds.virtualmap.internal.VirtualStateAccessor;
import com.swirlds.virtualmap.internal.merkle.VirtualRootNode;
import java.io.IOException;
import java.util.Objects;

/**
 * An implementation of {@link LearnerTreeView} for the virtual merkle. The learner during reconnect
 * needs access both to the original state and records, and the current reconnect state and records.
 * This implementation uses {@link Long} as the representation of a node and corresponds directly
 * to the path of the node.
 *
 * @param <K>
 * 		The key
 * @param <V>
 * 		The value
 */
public final class VirtualLearnerTreeView<K extends VirtualKey<? super K>, V extends VirtualValue>
        extends VirtualTreeViewBase<K, V> implements LearnerTreeView<Long> {

    /**
     * Some reasonable default initial capacity for the {@link BooleanBitSetQueue}s used for
     * storing {@link ExpectedLesson} data. If the value is too large, we use some more memory
     * than needed, if it is too small, we put pressure on the GC.
     */
    private static final int EXPECTED_BIT_SET_INITIAL_CAPACITY = 1024 * 1024;

    /**
     * A stashed null hash, which is used for any leaves which are null that we need to send
     * (specifically, leaf 2 for a tree with only a single leaf).
     */
    private static final Hash NULL_HASH = CryptographyHolder.get().getNullHash();

    /**
     * Handles removal of old nodes.
     */
    private ReconnectNodeRemover<K, V> nodeRemover;

    /**
     * Keys that have been encountered during this reconnect.
     */
    private final VirtualKeySet<K> encounteredKeys;

    /**
     * As part of tracking {@link ExpectedLesson}s, this keeps track of the "nodeAlreadyPresent" boolean.
     */
    private final BooleanBitSetQueue expectedNodeAlreadyPresent =
            new BooleanBitSetQueue(EXPECTED_BIT_SET_INITIAL_CAPACITY);

    /**
     * As part of tracking {@link ExpectedLesson}s, this keeps track of the combination of the
     * parent and index of the lesson.
     */
    private final ConcurrentBitSetQueue expectedChildren = new ConcurrentBitSetQueue();

    /**
     * As part of tracking {@link ExpectedLesson}s, this keeps track of the "original" long.
     */
    private final BooleanBitSetQueue expectedOriginalExists = new BooleanBitSetQueue(EXPECTED_BIT_SET_INITIAL_CAPACITY);

    /**
     * A {@link RecordAccessor} for getting access to the original records.
     */
    private final RecordAccessor<K, V> originalRecords;

    /**
     * True until we have handled our first leaf
     */
    private boolean firstLeaf = true;

    /**
     * Create a new {@link VirtualLearnerTreeView}.
     *
     * @param root
     * 		The root node of the <strong>reconnect</strong> tree. Cannot be null.
     * @param originalRecords
     * 		A {@link RecordAccessor} for accessing records from the unmodified <strong>original</strong> tree.
     * 		Cannot be null.
     * @param originalState
     * 		A {@link VirtualStateAccessor} for accessing state (first and last paths) from the
     * 		unmodified <strong>original</strong> tree. Cannot be null.
     * @param reconnectState
     * 		A {@link VirtualStateAccessor} for accessing state (first and last paths) from the
     * 		modified <strong>reconnect</strong> tree. We only use first and last leaf path from this state.
     * 		Cannot be null.
     */
    public VirtualLearnerTreeView(
            final VirtualRootNode<K, V> root,
            final RecordAccessor<K, V> originalRecords,
            final VirtualKeySet<K> encounteredKeys,
            final VirtualStateAccessor originalState,
            final VirtualStateAccessor reconnectState) {

        super(root, originalState, reconnectState);
        this.originalRecords = Objects.requireNonNull(originalRecords);
        this.encounteredKeys = encounteredKeys;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isRootOfState() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long getOriginalRoot() {
        return ROOT_PATH;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Hash getNodeHash(final Long originalChild) {
        // This method is only called on the Learner. The path given is the _ORIGINAL_ child. Each call to this
        // method will be made only for the original state from the original tree.

        // If the originalChild is null, then it means we're outside the range of valid nodes, and we will
        // return a NULL_HASH.
        if (originalChild == null) {
            return NULL_HASH;
        }

        // Make sure the path is valid for the original state
        checkValidNode(originalChild, originalState);

        // Get the original record (which may be in cache or on disk) to get the hash. We should look at optimizing
        // this in the future, so we don't read the whole record if we don't have to (for example, we could use
        // the loadLeafHash method).
        final VirtualRecord node = originalRecords.findRecord(originalChild);

        // We absolutely should have found the record.
        if (node == null) {
            throw new MerkleSynchronizationException("Could not find node for path " + originalChild);
        }

        // The hash must have been specified by this point. The original tree was hashed before
        // we started running on the learner, so either the hash is in cache or on disk, but it
        // definitely exists at this point. If it is null, something bad happened elsewhere.
        final Hash hash = node.getHash();
        if (hash == null) {
            throw new MerkleSynchronizationException("Node found, but hash was null. path=" + originalChild);
        }
        return hash;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void expectLessonFor(
            final Long parent, final int childIndex, final Long original, final boolean nodeAlreadyPresent) {
        expectedChildren.add(parent == null ? 0 : getChildPath(parent, childIndex));
        expectedNodeAlreadyPresent.add(nodeAlreadyPresent);
        expectedOriginalExists.add(original != null);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ExpectedLesson<Long> getNextExpectedLesson() {
        final long child = expectedChildren.remove();
        final long parent = getParentPath(child);
        final int index = isLeft(child) ? 0 : 1;
        final Long original = expectedOriginalExists.remove() ? child : null;
        final boolean nodeAlreadyPresent = expectedNodeAlreadyPresent.remove();
        return new ExpectedLesson<>(parent, index, original, nodeAlreadyPresent);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasNextExpectedLesson() {
        assert expectedOriginalExists.isEmpty() == expectedChildren.isEmpty()
                        && expectedChildren.isEmpty() == expectedNodeAlreadyPresent.isEmpty()
                : "All three should match";

        return !expectedOriginalExists.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long deserializeLeaf(final SerializableDataInputStream in) throws IOException {
        if (firstLeaf) {
            root.prepareForFirstLeaf();
            firstLeaf = false;
        }

        final VirtualLeafRecord<K, V> leaf = in.readSerializable();
        nodeRemover.newLeafNode(leaf.getPath(), leaf.getKey());
        root.handleReconnectLeaf(leaf); // may block if hashing is slower than ingest
        return leaf.getPath();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long deserializeInternal(final SerializableDataInputStream in) throws IOException {
        // We don't actually do anything useful with this deserialized long, other than return it.
        // Note: We may be able to omit this, but it requires some rework. See #4136
        final long node = in.readLong();
        if (node == ROOT_PATH) {
            // We send the first and last leaf path when reconnecting because we don't have access
            // to this information in the virtual root node at this point in the flow, even though
            // the info has already been sent and resides in the VirtualMapState that is a sibling
            // of the VirtualRootNode. This doesn't affect correctness or hashing.
            final long firstLeafPath = in.readLong();
            final long lastLeafPath = in.readLong();
            reconnectState.setFirstLeafPath(firstLeafPath);
            reconnectState.setLastLeafPath(lastLeafPath);
            root.prepareReconnectHashing(firstLeafPath, lastLeafPath);
            nodeRemover.setPathInformation(firstLeafPath, lastLeafPath);
        }
        nodeRemover.newInternalNode(node);
        return node;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void initialize() {
        // no-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void startThreads(final ThreadManager threadManager, final StandardWorkGroup workGroup) {
        nodeRemover = new ReconnectNodeRemover<>(
                threadManager,
                workGroup,
                originalRecords,
                encounteredKeys,
                originalState.getFirstLeafPath(),
                originalState.getLastLeafPath());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void close() {
        root.endLearnerReconnect();
        nodeRemover.close();
        encounteredKeys.close();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void markForInitialization(final Long node) {
        // no-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void releaseNode(final Long node) {
        // no-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setChild(final Long parent, final int childIndex, final Long child) {
        // No-op
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Long convertMerkleRootToViewType(final MerkleNode node) {
        throw new UnsupportedOperationException("Nested virtual maps not supported");
    }

    /**
     * Get the object responsible for removing nodes during the reconnect.
     */
    public ReconnectNodeRemover<K, V> getNodeRemover() {
        return nodeRemover;
    }
}
