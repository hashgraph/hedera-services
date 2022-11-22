/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.state.merkle;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.utility.CommonUtils;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.io.IOException;
import java.util.Objects;

/**
 * A {@link MerkleLeaf} containing a single String value. This is an immutable leaf -- once set, the
 * leaf value can never change. The maximum size for the leaf value is 128 bytes.
 */
final class StringLeaf extends PartialMerkleLeaf implements MerkleLeaf {
    // For serialization
    private static final long CLASS_ID = 99992323882382L;
    private static final int VERSION_0 = 0;
    private static final int CURRENT_VERSION = VERSION_0;

    /** The maximum length we permit for the string's value. This is in bytes, not in chars. */
    private static final int MAX_LENGTH = 128;

    /**
     * The value of the leaf. Will never be null, and will never have more than {@link #MAX_LENGTH}
     * bytes. Normally, this would be final, but the deserialization system of the hashgraph
     * platform does not permit it to be final.
     */
    private String value = "";

    /**
     * @deprecated Used by the deserialization system only
     */
    @Deprecated(since = "1.0")
    public StringLeaf() {}

    /**
     * Create a new instance with the given value for the leaf.
     *
     * @param value The value cannot be null.
     */
    public StringLeaf(@NonNull final String value) {
        Objects.requireNonNull(value);

        // Scrub the data by converting to normalized bytes, and check that we didn't get more bytes
        // than we should.
        final byte[] data = CommonUtils.getNormalisedStringBytes(value);
        if (data.length > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "The maximum *normalized* string length allowed is " + MAX_LENGTH);
        }

        // Now convert the normalized bytes back into a string. If we don't do this, then it may be
        // that we construct a leaf with a value which will not be identical after serializing it
        // out
        // and deserializing it back in!
        this.value = CommonUtils.getNormalisedStringFromBytes(data);
    }

    /**
     * Gets the value.
     *
     * @return A non-null string.
     */
    @NonNull
    public String getValue() {
        return value;
    }

    /** {@inheritDoc} */
    @Override
    @NonNull
    public StringLeaf copy() {
        throwIfImmutable();
        throwIfDestroyed();
        setImmutable(true);
        return new StringLeaf(value);
    }

    /** {@inheritDoc} */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /** {@inheritDoc} */
    @Override
    public void deserialize(@NonNull final SerializableDataInputStream in, int ver)
            throws IOException {
        // We cannot parse streams from future versions.
        if (ver > VERSION_0) {
            throw new IllegalArgumentException("The version number of the stream is too new.");
        }

        this.value = in.readNormalisedString(MAX_LENGTH);
    }

    /** {@inheritDoc} */
    @Override
    public void serialize(@NonNull final SerializableDataOutputStream out) throws IOException {
        out.writeNormalisedString(this.value);
    }

    /** {@inheritDoc} */
    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    /**
     * Convenience method for creating a {@link StringLeaf} from a stream, without having to use the
     * deprecated constructor. At the moment this is only used by tests, but when the platform
     * deserialization system is overhauled, it will need methods like this to work.
     *
     * @param in The input stream.
     * @param ver The version number
     * @return The deserialized {@link StringLeaf}
     * @throws IOException If there is a problem reading from the stream
     */
    public static @NonNull StringLeaf createFromStream(
            @NonNull final SerializableDataInputStream in, int ver) throws IOException {

        // We cannot parse streams from future versions.
        if (ver > VERSION_0) {
            throw new IllegalArgumentException("The version number of the stream is too new.");
        }

        final var value = in.readNormalisedString(MAX_LENGTH);
        return new StringLeaf(value);
    }

    /** {@inheritDoc} */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StringLeaf that = (StringLeaf) o;
        return value.equals(that.value);
    }

    /** {@inheritDoc} */
    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
