/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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
import com.hedera.hapi.node.state.throttles.ThrottleUsageSnapshots;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;

/**
 * The interface Writable schedule store.
 */
public interface WritableScheduleStore extends ReadableScheduleStore {

    /**
     * Delete a given schedule from this state.
     * Given the ID of a schedule and a consensus time, delete that ID from
     * this state as of the consensus time {@link Instant} provided.
     *
     * @param scheduleToDelete The ID of a schedule to be deleted.
     * @param consensusTime The current consensus time
     * @return the schedule
     * @throws IllegalStateException if the {@link ScheduleID} to be deleted is not present in this state, or the ID
     *     value has a mismatched realm or shard for this node.
     */
    Schedule delete(@Nullable ScheduleID scheduleToDelete, @NonNull Instant consensusTime);

    /**
     * Given the ID of a schedule, return a mutable reference to the schedule in this state.
     *
     * @param scheduleId The ID to find
     * @return the Schedule to modify
     */
    @Nullable
    Schedule getForModify(@NonNull ScheduleID scheduleId);

    /**
     * Add a schedule to this state.
     * If the schedule already exists it will be replaced.
     *
     * @param scheduleToAdd The schedule to add
     */
    void put(@NonNull Schedule scheduleToAdd);

    /**
     * Updates the usage of the throttles for the given consensus second.
     * @param consensusSecond The consensus second to track the usage for.
     * @param usageSnapshots The usage snapshots to track.
     */
    void trackUsage(long consensusSecond, @NonNull final ThrottleUsageSnapshots usageSnapshots);

    /**
     * Purges expired schedules from the store.
     *
     * @param firstSecondToExpire The consensus second of the first schedule to expire.
     * @param lastSecondToExpire  The consensus second of the last schedule to expire.
     */
    void purgeExpiredRangeClosed(long firstSecondToExpire, long lastSecondToExpire);
}
