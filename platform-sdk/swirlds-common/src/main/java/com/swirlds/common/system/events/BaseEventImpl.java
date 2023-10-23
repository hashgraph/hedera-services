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

package com.swirlds.common.system.events;

import com.swirlds.base.utility.ToStringBuilder;
import com.swirlds.common.io.OptionalSelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.util.Objects;

/**
 * This class implements a BaseEvent which pairs hashed data with the signature used to create the hash of the data.
 */
public class BaseEventImpl implements BaseEvent, OptionalSelfSerializable<EventSerializationOptions> {

    private static final long CLASS_ID = 0x56f60cb9c9d79397L;

    private static class ClassVersion {
        /**
         * Contains BaseEventHashedData and BaseEventUnhashedData.
         */
        public static final int ORIGINAL = 1;
    }

    private BaseEventHashedData hashedData;
    private BaseEventUnhashedData unhashedData;

    /**
     * Default empty constructor for deserialization use only
     */
    public BaseEventImpl() {}

    /**
     * Constructs a new BaseEventImpl with the given hashed and unhashed data.
     *
     * @param hashedData the hashed data
     * @param unhashedData the unhashed data
     */
    public BaseEventImpl(
            @NonNull final BaseEventHashedData hashedData, @NonNull final BaseEventUnhashedData unhashedData) {
        this.hashedData = Objects.requireNonNull(hashedData);
        this.unhashedData = Objects.requireNonNull(unhashedData);
        // FUTURE WORK: remove the update of the event descriptor when the other parent id is not in unhashed data.
        unhashedData.updateOtherParentEventDescriptor(hashedData);
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public void serialize(@NonNull final SerializableDataOutputStream out) throws IOException {
        serialize(out, EventSerializationOptions.FULL);
    }

    @Override
    public void serialize(
            @NonNull final SerializableDataOutputStream out, @NonNull final EventSerializationOptions option)
            throws IOException {
        out.writeOptionalSerializable(hashedData, false, option);
        out.writeSerializable(unhashedData, false);
    }

    @Override
    public void deserialize(@NonNull final SerializableDataInputStream in, final int version) throws IOException {
        hashedData = in.readSerializable(false, BaseEventHashedData::new);
        unhashedData = in.readSerializable(false, BaseEventUnhashedData::new);
        unhashedData.updateOtherParentEventDescriptor(hashedData);
    }

    @Override
    public int getVersion() {
        return ClassVersion.ORIGINAL;
    }

    @Override
    public BaseEventHashedData getHashedData() {
        if (hashedData == null) {
            throw new IllegalStateException("Hashed data is null, BaseEventImpl is not initialized properly");
        }
        return hashedData;
    }

    @Override
    public BaseEventUnhashedData getUnhashedData() {
        if (unhashedData == null) {
            throw new IllegalStateException("Unhashed data is null, BaseEventImpl is not initialized properly");
        }
        return unhashedData;
    }

    /**
     * Gets the signature used to create the hash of the data.
     *
     * @return the signature
     */
    public byte[] getSignature() {
        return unhashedData.getSignature();
    }

    @Override
    public boolean equals(@Nullable final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        BaseEventImpl baseEvent = (BaseEventImpl) o;
        return Objects.equals(hashedData, baseEvent.hashedData) && Objects.equals(unhashedData, baseEvent.unhashedData);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hashedData, unhashedData);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("baseEventHashedData", hashedData)
                .append("baseEventUnhashedData", unhashedData)
                .toString();
    }
}
