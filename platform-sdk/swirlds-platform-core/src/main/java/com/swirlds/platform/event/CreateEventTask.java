/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event;

/**
 * An intake task requesting the creation of a new event.
 */
public class CreateEventTask implements EventIntakeTask {

    /**
     * member whose event should be the other-parent (or -1 if none)
     */
    private final long otherId;

    public CreateEventTask(final long otherId) {
        super();
        this.otherId = otherId;
    }

    /**
     * Get the ID of the other-parent node of the event which this task represents
     *
     * @return the other-parent event/task ID
     */
    public long getOtherId() {
        return otherId;
    }

    @Override
    public String toString() {
        return "(new)(otherId:" + otherId + ")objHash:" + hashCode();
    }
}
