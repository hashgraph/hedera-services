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

package com.swirlds.platform.event.tipset;

import com.swirlds.common.utility.Startable;

/**
 * Evaluate the quality of tipset selections at event intake time.
 */
public class TipsetIntakeAnalyzer implements Startable {

    //	private record IntakeTask(EventImpl event, long minimumGenerationNonAncient) {
    //
    //	}
    //
    //	/**
    //	 * The ID of this node.
    //	 */
    //	private final long selfId;
    //
    //	/**
    //	 * Background work happens on this thread.
    //	 */
    //	private final QueueThread<IntakeTask> thread;
    //
    //	/**
    //	 * Computes and tracks tipsets for non-ancient events.
    //	 */
    //	private final TipsetTracker tracker;
    //
    //	/**
    //	 * Considers the effect of events on consensus from the window of this node's perspective.
    //	 */
    //	private final TipsetWindow window;
    //
    //	private static final RunningAverageMetric.Config TIPSET_SCORE_CONFIG =
    //			new RunningAverageMetric.Config("platform", "tipsetScore")
    //					.withDescription("The score, based on tipset advancements, of each new event created by this " +
    //							"node. A score of 0.0 means the event did not advance consensus at all, while a score " +
    //							"of 1.0 means that the event advanced consensus as much as a single event can.");
    //	private final RunningAverageMetric tipsetScore;
    //
    //	/**
    //	 * Create a new object for analyzing tipset advancements at event intake time.
    //	 *
    //	 * @param threadManager
    //	 * 		the thread manager to use
    //	 */
    //	public TipsetIntakeAnalyzer(
    //			final ThreadManager threadManager,
    //			final Metrics metrics,
    //			final long selfId,
    //			final AddressBook addressBook) {
    //
    //		this.selfId = selfId;
    //
    //		this.tracker = new TipsetTracker();
    //		this.window = new TipsetWindow(
    //				selfId,
    //				tracker,
    //				nodeId -> addressBook.getAddress(nodeId).getStake(),
    //				addressBook.getTotalStake());
    //
    //		thread = new QueueThreadConfiguration<IntakeTask>(threadManager)
    //				.setThreadName("event-intake")
    //				.setThreadName("tipset-analysis")
    //				.setHandler(this::handle)
    //				.build();
    //
    //		tipsetScore = metrics.getOrCreate(TIPSET_SCORE_CONFIG);
    //	}
    //
    //	/**
    //	 * Add an event to be analyzed on the background thread. Must be called on events in topological order.
    //	 *
    //	 * @param event
    //	 * 		the event that is being added
    //	 * @param minimumGenerationNonAncient
    //	 * 		the minimum generation that is not ancient at the current time
    //	 */
    //	public void addEvent(final EventImpl event, final Long minimumGenerationNonAncient) {
    //		abortAndLogIfInterrupted(() -> thread.put(new IntakeTask(event, minimumGenerationNonAncient)),
    //				"interrupted while attempting to insert event into tipset intake analyzer");
    //	}
    //
    /**
     * {@inheritDoc}
     */
    @Override
    public void start() {
        //		thread.start();
    }
    //
    //	/**
    //	 * Get the fingerprint of an event.
    //	 */
    //	private EventFingerprint getEventFingerprint(final EventImpl event) {
    //		return new EventFingerprint(
    //				event.getCreatorId(),
    //				event.getGeneration(),
    //				event.getHash());
    //	}
    //
    //	/**
    //	 * Get the fingerprints of an event's parents.
    //	 */
    //	private List<EventFingerprint> getParentFingerprints(final EventImpl event) {
    //		final List<EventFingerprint> parentFingerprints = new ArrayList<>(2);
    //		if (event.getSelfParent() != null) {
    //			parentFingerprints.add(getEventFingerprint(event.getSelfParent()));
    //		}
    //		if (event.getOtherParent() != null) {
    //			parentFingerprints.add(getEventFingerprint(event.getOtherParent()));
    //		}
    //		return parentFingerprints;
    //	}
    //
    //	/**
    //	 * Handle the next intake task.
    //	 */
    //	private void handle(final IntakeTask task) {
    //		tracker.setMinimumGenerationNonAncient(task.minimumGenerationNonAncient);
    //
    //		final EventImpl event = task.event;
    //
    //		if (event.getHash() == null) {
    //			// TODO How is this possible?
    //			CryptographyHolder.get().digestSync(event);
    //		}
    //
    //		final EventFingerprint fingerprint = getEventFingerprint(event);
    //		final List<EventFingerprint> parentFingerprints = getParentFingerprints(event);
    //
    //		tracker.addEvent(fingerprint, parentFingerprints);
    //
    //		if (event.getCreatorId() != selfId) {
    //			// We need to track all events, but we are only interested in reporting metrics
    //			// on the events created by this node at preconsensus time.
    //			return;
    //		}
    //
    //		final long score = window.addEvent(fingerprint);
    //		final double scoreRatio = score / (double) window.getMaximumPossibleScore();
    //
    //		tipsetScore.update(scoreRatio);
    //	}
}
