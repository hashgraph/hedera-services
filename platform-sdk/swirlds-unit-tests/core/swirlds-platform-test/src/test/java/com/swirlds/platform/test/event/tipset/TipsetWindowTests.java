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

package com.swirlds.platform.test.event.tipset;

import static com.swirlds.common.test.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.RandomUtils.randomHash;
import static com.swirlds.platform.Utilities.isSuperMajority;
import static com.swirlds.platform.event.tipset.Tipset.merge;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.swirlds.platform.event.tipset.EventFingerprint;
import com.swirlds.platform.event.tipset.Tipset;
import com.swirlds.platform.event.tipset.TipsetTracker;
import com.swirlds.platform.event.tipset.TipsetWindow;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TipsetWindow Tests")
class TipsetWindowTests {

//	@Test
//	@DisplayName("Basic Behavior Test")
//	void basicBehaviorTest() {
//		final Random random = getRandomPrintSeed(0);
//		final int nodeCount = 5;
//		final long windowId = random.nextLong(nodeCount);
//
//		final Map<Long, EventFingerprint> latestEvents = new HashMap<>();
//
//		final Map<Long, Long> weightMap = new HashMap<>();
//		long totalWeight = 0;
//		for (int i = 0; i < nodeCount; i++) {
//			final long weight = random.nextLong(1_000_000);
//			totalWeight += weight;
//			weightMap.put((long) i, weight);
//		}
//
//		final TipsetTracker tracker = new TipsetTracker();
//		final TipsetWindow window = new TipsetWindow(windowId, tracker, weightMap::get, totalWeight);
//
//		List<EventFingerprint> previousParents = List.of();
//		long runningAdvancementScore = 0;
//		Tipset previousSnapshot = window.getSnapshot();
//
//		for (int eventIndex = 0; eventIndex < 1000; eventIndex++) {
//			final long creator = random.nextLong(nodeCount);
//			final long generation;
//			if (latestEvents.containsKey(creator)) {
//				generation = latestEvents.get(creator).generation() + 1;
//			} else {
//				generation = 1;
//			}
//
//			final EventFingerprint selfParent = latestEvents.get(creator);
//			final EventFingerprint fingerprint = new EventFingerprint(creator, generation, randomHash(random));
//			latestEvents.put(creator, fingerprint);
//
//			// Select some nodes we'd like to be our parents.
//			final Set<Long> desiredParents = new HashSet<>();
//			final int maxParentCount = random.nextInt(nodeCount);
//			for (int parentIndex = 0; parentIndex < maxParentCount; parentIndex++) {
//				final long parent = random.nextInt(nodeCount);
//
//				// We are only trying to generate a random number of parents, the exact count is unimportant.
//				// So it doesn't matter if the actual number of parents is less than the number we requested.
//				if (parent == creator) {
//					continue;
//				}
//				desiredParents.add(parent);
//			}
//
//			// Select the actual parents.
//			final List<EventFingerprint> parentFingerprints = new ArrayList<>(desiredParents.size());
//			if (selfParent != null) {
//				parentFingerprints.add(selfParent);
//			}
//			for (final long parent : desiredParents) {
//				final EventFingerprint parentFingerprint = latestEvents.get(parent);
//				if (parentFingerprint != null) {
//					parentFingerprints.add(parentFingerprint);
//				}
//			}
//
//			tracker.addEvent(fingerprint, parentFingerprints);
//
//			if (creator != windowId) {
//				// The following validation only needs to happen for events created by the window node.
//
//				// Only do previous parent validation if we create two or more events in a row.
//				previousParents = List.of();
//
//				continue;
//			}
//
//			// Manually calculate the advancement score.
//			final List<Tipset> parentTipsets = new ArrayList<>(parentFingerprints.size());
//			for (final EventFingerprint parentFingerprint : parentFingerprints) {
//				parentTipsets.add(tracker.getTipset(parentFingerprint));
//			}
//			final long expectedAdvancementScoreChange = previousSnapshot.getAdvancementCount(
//					windowId,
//					merge(parentTipsets),
//					weightMap::get) - runningAdvancementScore;
//
//			// For events created by "this" node, check that the window is updated correctly.
//			final long advancementScoreChange = window.addEvent(fingerprint);
//
//			assertEquals(expectedAdvancementScoreChange, advancementScoreChange);
//
//			// Special case: if we create more than one event in a row and our current parents are a
//			// subset of the previous parents, then we should expect an advancement score of zero.
//			boolean subsetOfPreviousParents = true;
//			for (final EventFingerprint parentFingerprint : parentFingerprints) {
//				if (!previousParents.contains(parentFingerprint)) {
//					subsetOfPreviousParents = false;
//					break;
//				}
//			}
//			if (subsetOfPreviousParents) {
//				assertEquals(0, advancementScoreChange);
//			}
//			previousParents = parentFingerprints;
//
//			// Validate that the snapshot advances correctly.
//			runningAdvancementScore += advancementScoreChange;
//			if (isSuperMajority(runningAdvancementScore + weightMap.get(windowId), totalWeight)) {
//				// The snapshot should have been updated.
//				assertNotSame(previousSnapshot, window.getSnapshot());
//				previousSnapshot = window.getSnapshot();
//				runningAdvancementScore = 0;
//			} else {
//				// The snapshot should have not been updated.
//				assertSame(previousSnapshot, window.getSnapshot());
//			}
//		}
//	}
}
