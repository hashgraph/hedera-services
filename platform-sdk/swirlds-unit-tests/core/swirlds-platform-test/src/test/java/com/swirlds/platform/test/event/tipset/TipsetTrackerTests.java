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
import static com.swirlds.platform.event.tipset.Tipset.merge;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.swirlds.platform.event.tipset.EventFingerprint;
import com.swirlds.platform.event.tipset.Tipset;
import com.swirlds.platform.event.tipset.TipsetTracker;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TipsetTracker Tests")
class TipsetTrackerTests {

	private static void assertTipsetEquality(final Tipset expected, final Tipset actual, final long nodeCount) {
		assertEquals(expected.size(), actual.size());
		for (long nodeId = 0; nodeId < nodeCount; nodeId++) {
			assertEquals(expected.getTipGeneration(nodeId), actual.getTipGeneration(nodeId));
		}
	}

	@Test
	@DisplayName("Basic Behavior Test")
	void basicBehaviorTest() {
		final Random random = getRandomPrintSeed();

		final int nodeCount = random.nextInt(10, 20);

		final Map<Long, EventFingerprint> latestEvents = new HashMap<>();
		final Map<EventFingerprint, Tipset> expectedTipsets = new HashMap<>();

		final TipsetTracker tracker = new TipsetTracker(nodeCount, x -> (int) x, x -> 1);

		for (int eventIndex = 0; eventIndex < 1000; eventIndex++) {

			final long creator = random.nextLong(nodeCount);
			final long generation;
			if (latestEvents.containsKey(creator)) {
				generation = latestEvents.get(creator).generation() + 1;
			} else {
				generation = 1;
			}

			final EventFingerprint selfParent = latestEvents.get(creator);
			final EventFingerprint fingerprint = new EventFingerprint(creator, generation, randomHash(random));
			latestEvents.put(creator, fingerprint);

			// Select some nodes we'd like to be our parents.
			final Set<Long> desiredParents = new HashSet<>();
			final int maxParentCount = random.nextInt(nodeCount);
			for (int parentIndex = 0; parentIndex < maxParentCount; parentIndex++) {
				final long parent = random.nextInt(nodeCount);

				// We are only trying to generate a random number of parents, the exact count is unimportant.
				// So it doesn't matter if the actual number of parents is less than the number we requested.
				if (parent == creator) {
					continue;
				}
				desiredParents.add(parent);
			}

			// Select the actual parents.
			final List<EventFingerprint> parentFingerprints = new ArrayList<>(desiredParents.size());
			if (selfParent != null) {
				parentFingerprints.add(selfParent);
			}
			for (final long parent : desiredParents) {
				final EventFingerprint parentFingerprint = latestEvents.get(parent);
				if (parentFingerprint != null) {
					parentFingerprints.add(parentFingerprint);
				}
			}

			final Tipset newTipset = tracker.addEvent(fingerprint, parentFingerprints);
			assertSame(newTipset, tracker.getTipset(fingerprint));

			// Now, reconstruct the tipset manually, and make sure it matches what we were expecting.
			final List<Tipset> parentTipsets = new ArrayList<>(parentFingerprints.size());
			for (final EventFingerprint parentFingerprint : parentFingerprints) {
				parentTipsets.add(expectedTipsets.get(parentFingerprint));
			}

			final Tipset expectedTipset;
			if (parentTipsets.isEmpty()) {
				expectedTipset = new Tipset(nodeCount, x -> (int) x, x -> 1).advance(creator, generation);
			} else {
				expectedTipset = merge(parentTipsets);
			}

			expectedTipsets.put(fingerprint, expectedTipset);
			assertTipsetEquality(expectedTipset, newTipset, nodeCount);
		}

		// At the very end, we shouldn't see any modified tipsets
		for (final EventFingerprint fingerprint : expectedTipsets.keySet()) {
			assertTipsetEquality(expectedTipsets.get(fingerprint), tracker.getTipset(fingerprint), nodeCount);
		}

		// Slowly advance the minimum generation, we should see tipsets disappear as we go.
		long minimumGenerationNonAncient = 0;
		while (tracker.size() > 0) {
			minimumGenerationNonAncient += random.nextInt(1, 5);
			tracker.setMinimumGenerationNonAncient(minimumGenerationNonAncient);
			for (final EventFingerprint fingerprint : expectedTipsets.keySet()) {
				if (fingerprint.generation() < minimumGenerationNonAncient) {
					assertNull(tracker.getTipset(fingerprint));
				} else {
					assertTipsetEquality(expectedTipsets.get(fingerprint), tracker.getTipset(fingerprint), nodeCount);
				}
			}
		}
	}
}
