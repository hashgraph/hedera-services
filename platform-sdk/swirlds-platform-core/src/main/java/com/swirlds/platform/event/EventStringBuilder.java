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

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.system.events.BaseEvent;
import com.swirlds.common.system.events.BaseEventHashedData;
import com.swirlds.common.system.events.BaseEventUnhashedData;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.platform.internal.EventImpl;

/**
 * A class used to convert an event into a string
 */
public final class EventStringBuilder {
    /** number of bytes of a hash to write */
    private static final int NUM_BYTES_HASH = 4;

    /** used for building the event string */
    private final StringBuilder sb = new StringBuilder();
    /** hashed data of an event */
    private final BaseEventHashedData hashedData;
    /** unhashed data of an event */
    private final BaseEventUnhashedData unhashedData;

    private EventStringBuilder(final BaseEventHashedData hashedData, final BaseEventUnhashedData unhashedData) {
        this.hashedData = hashedData;
        this.unhashedData = unhashedData;
    }

    private EventStringBuilder(final String errorString) {
        this(null, null);
        sb.append(errorString);
    }

    public static EventStringBuilder builder(final EventImpl event) {
        if (event == null) {
            return new EventStringBuilder("(EventImpl=null)");
        }
        return builder(event.getBaseEvent());
    }

    public static EventStringBuilder builder(final BaseEvent event) {
        if (event == null) {
            return new EventStringBuilder("(BaseEvent=null)");
        }
        return builder(event.getHashedData(), event.getUnhashedData());
    }

    public static EventStringBuilder builder(
            final BaseEventHashedData hashedData, final BaseEventUnhashedData unhashedData) {
        if (hashedData == null) {
            return new EventStringBuilder("(HashedData=null)");
        }
        if (unhashedData == null) {
            return new EventStringBuilder("(UnhashedData=null)");
        }

        return new EventStringBuilder(hashedData, unhashedData);
    }

    private boolean isNull() {
        return hashedData == null || unhashedData == null;
    }

    public EventStringBuilder appendEvent() {
        if (isNull()) {
            return this;
        }
        appendShortEvent(hashedData.getCreatorId(), hashedData.getGeneration(), hashedData.getHash());
        return this;
    }

    public EventStringBuilder appendSelfParent() {
        if (isNull()) {
            return this;
        }
        sb.append(" sp");
        appendShortEvent(hashedData.getCreatorId(), hashedData.getSelfParentGen(), hashedData.getSelfParentHash());
        return this;
    }

    public EventStringBuilder appendOtherParent() {
        if (isNull()) {
            return this;
        }
        sb.append(" op");
        appendShortEvent(unhashedData.getOtherId(), hashedData.getOtherParentGen(), hashedData.getOtherParentHash());
        return this;
    }

    /**
     * Append a short string representation of an event with the supplied information
     *
     * @param creatorId
     * 		creator ID of the event
     * @param generation
     * 		generation of the event
     * @param hash
     * 		the hash of the event
     */
    private void appendShortEvent(final long creatorId, final long generation, final Hash hash) {
        sb.append('(');
        if (creatorId == EventConstants.CREATOR_ID_UNDEFINED || generation == EventConstants.GENERATION_UNDEFINED) {
            sb.append("none)");
            return;
        }
        sb.append(creatorId).append(',').append(generation).append(',');
        appendHash(hash);
        sb.append(')');
    }

    /**
     * Append the shortened hash value to the StringBuilder
     *
     * @param hash
     * 		the hash to append
     */
    private void appendHash(final Hash hash) {
        if (hash == null) {
            sb.append("null");
        } else {
            sb.append(CommonUtils.hex(hash.getValue(), NUM_BYTES_HASH));
        }
    }

    public String build() {
        return sb.toString();
    }
}
