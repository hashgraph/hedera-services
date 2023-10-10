/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.creation.tipset;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Represents a slice of the hashgraph, containing one "tip" from each event creator.
 */
public class Tipset {

    private final AddressBook addressBook;

    /**
     * The tip generations, indexed by node index.
     */
    private final long[] tips;

    /**
     * Create an empty tipset.
     *
     * @param addressBook the current address book
     */
    public Tipset(@NonNull final AddressBook addressBook) {
        this.addressBook = Objects.requireNonNull(addressBook);
        tips = new long[addressBook.getSize()];

        // Necessary because we currently start at generation 0, not generation 1.
        Arrays.fill(tips, -1);
    }

    /**
     * Build an empty tipset (i.e. where all generations are -1) using another tipset as a template.
     *
     * @param tipset the tipset to use as a template
     * @return a new empty tipset
     */
    private static @NonNull Tipset buildEmptyTipset(@NonNull final Tipset tipset) {
        return new Tipset(tipset.addressBook);
    }

    /**
     * <p>
     * Merge a list of tipsets together.
     *
     * <p>
     * The generation for each node ID will be equal to the maximum generation found for that node ID from all source
     * tipsets.
     *
     * @param tipsets the tipsets to merge, must be non-empty, tipsets must be constructed from the same address book or
     *                else this method has undefined behavior
     * @return a new tipset
     */
    public static @NonNull Tipset merge(@NonNull final List<Tipset> tipsets) {
        Objects.requireNonNull(tipsets, "tipsets must not be null");
        if (tipsets.isEmpty()) {
            throw new IllegalArgumentException("Cannot merge an empty list of tipsets");
        }

        final int length = tipsets.get(0).tips.length;
        final Tipset newTipset = buildEmptyTipset(tipsets.get(0));

        for (int index = 0; index < length; index++) {
            long max = -1;
            for (final Tipset tipSet : tipsets) {
                max = Math.max(max, tipSet.tips[index]);
            }
            newTipset.tips[index] = max;
        }

        return newTipset;
    }

    /**
     * Get the tip generation for a given node
     *
     * @param nodeId the node in question
     * @return the tip generation for the node
     */
    public long getTipGenerationForNode(@NonNull final NodeId nodeId) {
        return tips[addressBook.getIndexOfNodeId(nodeId)];
    }

    /**
     * Get the number of tips currently being tracked.
     *
     * @return the number of tips
     */
    public int size() {
        return tips.length;
    }

    /**
     * Advance a single tip within the tipset.
     *
     * @param creator    the node ID of the creator of the event
     * @param generation the generation of the event
     * @return this object
     */
    public @NonNull Tipset advance(@NonNull final NodeId creator, final long generation) {
        final int index = addressBook.getIndexOfNodeId(creator);
        tips[index] = Math.max(tips[index], generation);
        return this;
    }

    /**
     * <p>
     * Get the combined weight of all nodes which experienced a tip advancement between this tipset and another tipset.
     * Note that this method ignores advancement contributions from this node.
     * </p>
     *
     * <p>
     * A tip advancement is defined as an increase in the tip generation for a node ID. The exception to this rule is
     * that an increase in generation for the self ID is never counted as a tip advancement. The tip advancement
     * weight is defined as the sum of all remaining tip advancements after being appropriately weighted.
     * </p>
     *
     * <p>
     * Advancements of non-zero stake nodes are tracked via {@link TipsetAdvancementWeight#advancementWeight()}, while
     * advancements of zero stake nodes are tracked via {@link TipsetAdvancementWeight#zeroWeightAdvancementCount()}.
     *
     * @param selfId compute the advancement weight relative to this node ID
     * @param that   the tipset to compare to
     * @return the tipset advancement weight
     */
    @NonNull
    public TipsetAdvancementWeight getTipAdvancementWeight(@NonNull final NodeId selfId, @NonNull final Tipset that) {
        long nonZeroWeight = 0;
        long zeroWeightCount = 0;

        final int selfIndex = addressBook.getIndexOfNodeId(selfId);
        for (int index = 0; index < tips.length; index++) {
            if (index == selfIndex) {
                // We don't consider self advancement here, since self advancement does nothing to help consensus.
                continue;
            }

            if (this.tips[index] < that.tips[index]) {
                final NodeId nodeId = addressBook.getNodeId(index);
                final Address address = addressBook.getAddress(nodeId);

                if (address.getWeight() == 0) {
                    zeroWeightCount += 1;
                } else {
                    nonZeroWeight += address.getWeight();
                }
            }
        }

        return TipsetAdvancementWeight.of(nonZeroWeight, zeroWeightCount);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("(");
        for (int index = 0; index < tips.length; index++) {
            sb.append(addressBook.getNodeId(index)).append(":").append(tips[index]);
            if (index < tips.length - 1) {
                sb.append(", ");
            }
        }
        sb.append(")");
        return sb.toString();
    }
}
