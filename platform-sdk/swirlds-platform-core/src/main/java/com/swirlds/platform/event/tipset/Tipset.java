/*
 * Copyright 2016-2023 Hedera Hashgraph, LLC
 *
 * This software is the confidential and proprietary information of
 * Hedera Hashgraph, LLC. ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Hedera Hashgraph.
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. HEDERA HASHGRAPH SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */

package com.swirlds.platform.event.tipset;

import static com.swirlds.common.formatting.StringFormattingUtils.formattedList;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Represents a slice of the hashgraph, containing one "tip" from each event creator.
 */
public class Tipset {

	private final Map<Long, Long> tips = new HashMap<>();

	/**
	 * Create an empty tipset.
	 */
	public Tipset() {

	}

	/**
	 * <p>
	 * Merge a list of tipsets together.
	 * </p>
	 *
	 * <p>
	 * The resulting tipset will contain the union of the node IDs of all source tipsets. The
	 * generation for each node ID will be equal to the maximum generation found for that
	 * node ID from all source tipsets. If a tipset does not contain a generation for a node
	 * ID, the generation for that node ID is considered to be 0.
	 * </p>
	 *
	 * @param tipsets
	 * 		the tipsets to merge
	 * @return a new tipset
	 */
	public static Tipset merge(final List<Tipset> tipsets) {
		final Tipset newTipset = new Tipset();

		final Set<Long> creators = new HashSet<>();
		for (final Tipset tipset : tipsets) {
			creators.addAll(tipset.tips.keySet());
		}

		for (final long creator : creators) {
			long max = 0;
			for (final Tipset tipSet : tipsets) {
				max = Math.max(max, tipSet.tips.getOrDefault(creator, 0L));
			}
			newTipset.tips.put(creator, max);
		}

		return newTipset;
	}

	/**
	 * Get the tip generation for a given node ID. Returns 0 if this node has not been added to the tipset.
	 *
	 * @param nodeId
	 * 		the node ID in question
	 * @return the tip generation for the node ID
	 */
	public long getTipGeneration(final long nodeId) {
		return tips.getOrDefault(nodeId, 0L);
	}

	/**
	 * Get the number of tips currently being tracked.
	 *
	 * @return the number of tips
	 */
	public int size() {
		return tips.size();
	}

	/**
	 * Advance a single tip within the tipset.
	 *
	 * @return this object
	 */
	public Tipset advance(final long creatorId, final long generation) {
		if (generation > tips.getOrDefault(creatorId, 0L)) {
			tips.put(creatorId, generation);
		}
		return this;
	}

	/**
	 * <p>
	 * Get the number of tip advancements, weighted using the provided function, between this tipset and another tipset.
	 * </p>
	 *
	 * <p>
	 * A tip advancement is defined as an increase in the tip generation for a node ID. The exception to this rule
	 * is that an increase in generation for the target node ID is never counted as a tip advancement. The tip
	 * advancement count is defined as the sum of all tip advancements after being appropriately weighted.
	 * </p>
	 *
	 * @param nodeId
	 * 		compute the advancement count relative to this node ID
	 * @param that
	 * 		the tipset to compare to
	 * @param weights
	 * 		a function that assigns each creator a weight
	 * @return the number of tip advancements to get from this tipset to that tipset
	 */
	public long getAdvancementCount(final long nodeId, final Tipset that, final Function<Long, Long> weights) {
		long count = 0;

		for (final long creatorId : that.tips.keySet()) {
			if (creatorId == nodeId) {
				// We don't consider self advancement here, since self advancement does nothing to help consensus.
				continue;
			}
			if (this.tips.getOrDefault(creatorId, 0L) < that.tips.get(creatorId)) {
				count += weights.apply(creatorId);
			}
		}

		return count;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("(");
		final Iterator<String> iterator = tips.keySet().stream().sorted().map(x -> x + ":" + tips.get(x)).iterator();
		formattedList(sb, iterator);
		sb.append(")");

		return sb.toString();
	}
}
