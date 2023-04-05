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

package com.swirlds.platform.components;

import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.STALE_EVENTS;
import static com.swirlds.logging.LogMarker.SYNC;

import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.platform.SettingsProvider;
import com.swirlds.platform.event.CreateEventTask;
import com.swirlds.platform.event.EventIntakeTask;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.event.ValidEvent;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.EventIntakeMetrics;
import com.swirlds.platform.sync.SyncManager;
import com.swirlds.platform.sync.SyncResult;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Responsible for creating and enqueuing event tasks. Event tasks can either be {@link GossipEvent} or {@link
 * CreateEventTask}
 */
public class EventTaskCreator {
    /** use this for all logging, as controlled by the optional data/log4j2.xml file */
    private static final Logger logger = LogManager.getLogger(EventTaskCreator.class);

    /** immutable current version of the address book. (will later store one per round) */
    private final AddressBook addressBook; // if this code is changed to non-final, make it volatile

    /** the object that tracks statistics */
    private final EventIntakeMetrics eventIntakeMetrics;

    /** the member ID of the member running the platform using this hashgraph */
    private final NodeId selfId;

    /** The {@link EventMapper} for this hashgraph */
    private final EventMapper eventMapper;

    /** A {@link QueueThread} that handles event intake */
    private final BlockingQueue<EventIntakeTask> eventIntakeQueue;

    /** provides access to settings */
    private final SettingsProvider settings;

    /** supplies the Random object */
    private final Supplier<Random> random;

    /** manages sync related tasks */
    private final SyncManager syncManager;

    /**
     * constructor that is given the platform using the hashgraph, and the initial addressBook (which can
     * change)
     *
     * @param eventMapper
     * 		event mapper
     * @param addressBook
     * 		the addressBook
     * @param selfId
     * 		the ID of the platform this hashgraph is running on
     * @param eventIntakeMetrics
     * 		tracks metrics
     * @param eventIntakeQueue
     * 		the queue add tasks to
     * @param settings
     * 		provides access to settings
     * @param syncManager
     * 		decides if an event should be created
     * @param random
     * 		supplies the random instance to use
     */
    public EventTaskCreator(
            final EventMapper eventMapper,
            final AddressBook addressBook,
            final NodeId selfId,
            final EventIntakeMetrics eventIntakeMetrics,
            final BlockingQueue<EventIntakeTask> eventIntakeQueue,
            final SettingsProvider settings,
            final SyncManager syncManager,
            final Supplier<Random> random) {
        this.eventMapper = eventMapper;
        this.eventIntakeMetrics = eventIntakeMetrics;
        this.selfId = selfId;
        this.addressBook = addressBook.copy();
        this.addressBook.seal();
        this.eventIntakeQueue = eventIntakeQueue;
        this.settings = settings;
        this.syncManager = syncManager;
        this.random = random;
    }

    /**
     * Creates the appropriate events after a sync has finished successfully. If an event should be created according to
     * the rules in {@link SyncManager#shouldCreateEvent(SyncResult)}, a self event with an other-parent of the node we
     * just synced with is added to the intake queue. An additional event may be created with a different other-parent.
     *
     * @param result
     * 		information about the sync that just finished successfully
     */
    public void syncDone(final SyncResult result) {
        final boolean shouldCreateEvent = syncManager.shouldCreateEvent(result);
        eventIntakeMetrics.eventCreation(shouldCreateEvent);
        if (!shouldCreateEvent) {
            // we are not creating any events
            return;
        }

        createEvent(result.getOtherId().getId());

        logger.debug(SYNC.getMarker(), "{} created event for sync otherId:{}", selfId, result.getOtherId());

        randomEvent();

        rescueChildlessEvents();
    }

    private void randomEvent() {
        final Random r = random.get();
        // maybe create an event with a random other parent
        if (settings.getRandomEventProbability() > 0 && r.nextInt(settings.getRandomEventProbability()) == 0) {
            final long randomOtherId = r.nextInt(addressBook.getSize());
            // we don't want to create an event with selfId==otherId
            if (!selfId.equalsMain(randomOtherId)) {
                createEvent(randomOtherId);
                logger.debug(SYNC.getMarker(), "{} created random event otherId:{}", selfId, randomOtherId);
            }
        }
    }

    /**
     * If an event on this node has no children, then generate a new child event
     * for it, based on a probability value defined by {@code Settings.rescueChildlessInverseProbability}.
     *
     * This functionality may be deprecated in future.
     */
    public void rescueChildlessEvents() {
        if (settings.getRescueChildlessInverseProbability() <= 0) {
            return;
        }

        for (int i = 0; i < addressBook.getSize(); i++) {
            if (selfId.equalsMain(i)) {
                // we don't rescue our own event, this might have been the cause of a reconnect issue
                continue;
            }

            if (eventMapper.doesMostRecentEventHaveDescendants(i)) {
                // not childless
                continue;
            }
            final EventImpl event = eventMapper.getMostRecentEvent(i);
            if (event == null) {
                // we have no last event for this member
                continue;
            }

            // Decide, with probability = 1 / Settings.rescueChildlessInverseProbability, to create an other-child
            // for a childless event.
            if (random.get().nextInt(settings.getRescueChildlessInverseProbability()) == 0) {
                logger.info(STALE_EVENTS.getMarker(), "Creating child for childless event {}", event::toShortString);
                createEvent(event.getCreatorId());
                eventIntakeMetrics.rescuedEvent();
            }
        }
    }

    /**
     * Insert an event task to create a self-event into the hashgraph intake queue. The created event
     * will have other-parent specified by the given node ID
     *
     * @param otherId
     * 		the ID of the other-parent of the event to be created
     */
    public void createEvent(final long otherId) {
        addEvent(new CreateEventTask(otherId));
    }

    /**
     * Add an event task to the queue to be instantiated by other threads in parallel. The instantiated event will
     * eventually be added to the hashgraph by the pollIntakeQueue method.
     *
     * @param intakeTask
     * 		a task whose event is to be added to the hashgraph
     */
    public void addEvent(final EventIntakeTask intakeTask) {
        try {
            eventIntakeQueue.put(intakeTask);
        } catch (InterruptedException e) {
            // should never happen, and we don't have a simple way of recovering from it
            logger.error(
                    EXCEPTION.getMarker(), "CRITICAL ERROR, adding intakeTask to the event intake queue failed", e);
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Add a newly created event to the intake queue
     *
     * @param event
     * 		the newly created event
     */
    public void createdEvent(final GossipEvent event) {
        addEvent(new ValidEvent(event));
    }
}
