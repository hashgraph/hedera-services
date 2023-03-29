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

package com.swirlds.platform.test.event.tipset;

import org.junit.jupiter.api.DisplayName;

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
