/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.schedule;

import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.state.schedule.Schedule;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Optional;

/**
 * Provides read-only methods for interacting with the underlying data storage mechanisms for
 * working with Schedules.
 * There are three ways to look up schedules.  A single schedule by {@link ScheduleID}, a list
 * of schedules by "equality" (a hash comparison for efficiency), and a list of schedules by
 * "expiration second" (for quickly finding expired schedules to remove).
 */
public interface ReadableScheduleStore {

    /**
     * Gets the schedule with the given {@link ScheduleID}. If there is no schedule with given ID
     * returns null.  We do not use {@link Optional} here because null is not a valid value, so
     * there is no need to distinguish between empty and null.
     *
     * @param id given id for the schedule
     *
     * @return the schedule with the given id
     */
    @Nullable
    Schedule get(final @Nullable ScheduleID id);

    /**
     * Get a set of schedules that are "hash equal" to the provided Schedule.
     * Two schedules are "hash equal" if the hash of the original create transaction for one schedule
     * is identical to the hash of the create transaction for the other schedule.  This is primarily
     * used to ensure that we do not permit duplicate schedules to be created. Note that hash equality
     * is necessary, but not sufficient, so the return values must be compared field-by-field to ensure
     * actual equality (within the constraints of schedule duplication) before asserting that the requested
     * schedule is a duplicate.
     * @param scheduleToMatch a {@link Schedule} to match according to hash
     * @return a {@link List<Schedule>} of entries that have the same hash as the provided schedule.
     *     These may not actually be equal to the provided schedule, and further comparison should be performed.
     */
    @Nullable
    public List<Schedule> getByEquality(final @NonNull Schedule scheduleToMatch);

    /**
     * Given a time as seconds since the epoch, find all schedules currently in state that expire at that time.
     * The {@link List<Schedule>} returned will contain all {@link Schedule} entries in the system that have a
     * calculated expiration time that matches the requested value.  The check is no more precise than one second,
     * so the list may be quite large (significantly larger than the "schedules created" throttle).
     *
     * @param expirationTime the number of seconds since the epoch that describes the expiration time of schedules
     *     to be returned.
     * @return a {@link List<Schedule>} of entries that have expiration times within the requested second.
     */
    @Nullable
    public List<Schedule> getByExpirationSecond(final long expirationTime);

    /**
     * Returns the number of schedules in state, for use in enforcing creation limits.
     *
     * @return the number of schedules in state
     */
    long numSchedulesInState();
}
