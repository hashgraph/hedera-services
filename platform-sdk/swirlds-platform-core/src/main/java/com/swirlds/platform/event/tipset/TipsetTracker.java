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

import static com.swirlds.platform.event.tipset.Tipset.merge;

import com.swirlds.common.sequence.map.SequenceMap;
import com.swirlds.common.sequence.map.StandardSequenceMap;
import java.util.ArrayList;
import java.util.List;

/**
 * Computes and tracks tipsets for non-ancient events.
 */
public class TipsetTracker {

	private final SequenceMap<EventFingerprint, Tipset> tipsets;

	public TipsetTracker() {
		tipsets = new StandardSequenceMap<>(
				0,
				1024, // TODO meet or exceed maximum generations allowed in memory past ancient
				EventFingerprint::generation);
	}

	/**
	 * Set the minimum generation that is not considered ancient.
	 *
	 * @param minimumGenerationNonAncient
	 * 		the minimum non-ancient generation, all lower generations are ancient
	 */
	public void setMinimumGenerationNonAncient(final long minimumGenerationNonAncient) {
		tipsets.shiftWindow(minimumGenerationNonAncient);
	}

	/**
	 * Add a new event to the tracker.
	 *
	 * @param eventFingerprint
	 * 		the fingerprint of the event to add
	 * @param parents
	 * 		the parents of the event being added
	 * @return the tipset for the event that was added
	 */
	public Tipset addEvent(final EventFingerprint eventFingerprint, final List<EventFingerprint> parents) {
		final List<Tipset> parentTipsets = new ArrayList<>(parents.size());
		for (final EventFingerprint parent : parents) {
			final Tipset parentTipset = tipsets.get(parent);
			if (parentTipset != null) {
				parentTipsets.add(parentTipset);
			}
		}

		final Tipset eventTipset = merge(parentTipsets)
				.advance(eventFingerprint.creator(), eventFingerprint.generation());

		tipsets.put(eventFingerprint, eventTipset);

		return eventTipset;
	}

	/**
	 * Get the tipset of an event, or null if the event is not being tracked.
	 *
	 * @param eventFingerprint
	 * 		the fingerprint of the event
	 * @return the tipset of the event
	 */
	public Tipset getTipset(final EventFingerprint eventFingerprint) {
		return tipsets.get(eventFingerprint);
	}

	/**
	 * Get number of tipsets being tracked.
	 */
	public int size() {
		return tipsets.getSize();
	}
}
