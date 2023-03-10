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

package com.swirlds.common.io.streams;

import static com.swirlds.common.io.streams.SerializableStreamConstants.NULL_CLASS_ID;
import static com.swirlds.common.io.streams.SerializableStreamConstants.NULL_LIST_ARRAY_LENGTH;
import static com.swirlds.common.io.streams.SerializableStreamConstants.NULL_VERSION;
import static com.swirlds.common.io.streams.SerializableStreamConstants.SERIALIZATION_PROTOCOL_VERSION;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDet;
import com.swirlds.common.io.exceptions.ClassNotFoundException;
import com.swirlds.common.io.exceptions.InvalidVersionException;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.common.utility.ThrowingFunction;
import com.swirlds.common.utility.ValueReference;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/**
 * A drop-in replacement for {@link DataInputStream}, which handles SerializableDet classes specially.
 * It is designed for use with the SerializableDet interface, and its use is described there.
 */
public class SerializableDataInputStream extends AugmentedDataInputStream {

    private static final int PROTOCOL_VERSION = SERIALIZATION_PROTOCOL_VERSION;

    /**
     * Creates a stream capable of deserializing serializable objects.
     *
     * @param in
     * 		the specified input stream
     */
    public SerializableDataInputStream(final InputStream in) {
        super(in);
    }

    /**
     * Reads the protocol version written by {@link SerializableDataOutputStream#writeProtocolVersion()} and saves it
     * internally. From this point on, it will use this version number to deserialize.
     *
     * @throws IOException
     * 		thrown if any IO problems occur
     */
    public void readProtocolVersion() throws IOException {
        final int protocolVersion = readInt();
        if (protocolVersion != PROTOCOL_VERSION) {
            throw new IOException("invalid protocol version " + protocolVersion);
        }
    }

    /**
     * Reads a {@link SerializableDet} from a stream and returns it. The instance will be created using the
     * {@link ConstructableRegistry}. The instance must have previously been written using
     * {@link SerializableDataOutputStream#writeSerializable(SelfSerializable, boolean)} (SerializableDet, boolean)}
     * with {@code writeClassId} set to true, otherwise we
     * cannot know what the class written is.
     *
     * @param <T>
     * 		the implementation of {@link SelfSerializable} used
     * @return An instance of the class previously written
     * @throws IOException
     * 		thrown if any IO problems occur
     */
    public <T extends SelfSerializable> T readSerializable() throws IOException {
        return readSerializable(true, SerializableDataInputStream::registryConstructor);
    }

    /**
     * Uses the provided {@code serializable} to read its data from the stream.
     *
     * @param serializableConstructor
     * 		a constructor for the instance written in the stream
     * @param readClassId
     * 		set to true if the class ID was written to the stream
     * @param <T>
     * 		the implementation of {@link SelfSerializable} used
     * @return the same object that was passed in, returned for convenience
     * @throws IOException
     * 		thrown if any IO problems occur
     */
    public <T extends SelfSerializable> T readSerializable(
            final boolean readClassId, final Supplier<T> serializableConstructor) throws IOException {

        CommonUtils.throwArgNull(serializableConstructor, "serializableConstructor");
        return readSerializable(readClassId, id -> serializableConstructor.get());
    }

    /**
     * Throws an exception if the version is not supported.
     */
    protected void validateVersion(final SerializableDet object, final int version) throws InvalidVersionException {
        if (version < object.getMinimumSupportedVersion() || version > object.getVersion()) {
            throw new InvalidVersionException(version, object);
        }
    }

    /**
     * Called when the class ID of an object becomes known. This method is a hook for the debug stream.
     *
     * @param classId
     * 		the class ID of the current object being deserialized
     */
    protected void recordClassId(final long classId) {
        // debug framework can override
    }

    /**
     * Called when the class ID of an object becomes known. This method is a hook for the debug stream.
     *
     * @param o
     * 		the object that is being deserialized
     */
    protected void recordClass(final Object o) {
        // debug framework can override
    }

    /**
     * Same as {@link #readSerializable(boolean, Supplier)} except that the constructor takes a class ID
     */
    private <T extends SelfSerializable> T readSerializable(
            final boolean readClassId, final ThrowingFunction<Long, T, IOException> serializableConstructor)
            throws IOException {

        final Long classId;
        if (readClassId) {
            classId = readLong();
            recordClassId(classId);
            if (classId == NULL_CLASS_ID) {
                return null;
            }
        } else {
            classId = null;
        }

        final int version = readInt();
        if (version == NULL_VERSION) {
            return null;
        }

        final T serializable = serializableConstructor.apply(classId);
        recordClass(serializable);

        validateVersion(serializable, version);
        serializable.deserialize(this, version);
        return serializable;
    }

    /**
     * Read a sequence of serializable objects and pass them to a callback method.
     *
     * @param maxSize
     * 		the maximum allowed size
     * @param callback
     * 		this method is passed each object in the sequence
     * @param <T>
     * 		the type of the objects in the sequence
     */
    public <T extends SelfSerializable> void readSerializableIterableWithSize(
            final int maxSize, final Consumer<T> callback) throws IOException {

        int size = readInt();
        checkLengthLimit(size, maxSize);
        readSerializableIterableWithSizeInternal(
                size, true, SerializableDataInputStream::registryConstructor, callback);
    }

    /**
     * Read a sequence of serializable objects and pass them to a callback method.
     *
     * @param maxSize
     * 		the maximum number of objects to read
     * @param readClassId
     * 		if true then the class ID needs to be read
     * @param serializableConstructor
     * 		a method that takes a class ID and provides a constructor
     * @param callback
     * 		the callback method where each object is passed when it is deserialized
     * @param <T>
     * 		the type of the objects being deserialized
     */
    public <T extends SelfSerializable> void readSerializableIterableWithSize(
            final int maxSize,
            final boolean readClassId,
            final Supplier<T> serializableConstructor,
            final Consumer<T> callback)
            throws IOException {
        final int size = readInt();
        checkLengthLimit(size, maxSize);
        readSerializableIterableWithSizeInternal(size, readClassId, id -> serializableConstructor.get(), callback);
    }

    /**
     * Read a sequence of serializable objects and pass them to a callback method.
     *
     * @param size
     * 		the number of objects to read
     * @param readClassId
     * 		if true then the class ID needs to be read
     * @param serializableConstructor
     * 		a method that takes a class ID and provides a constructor
     * @param callback
     * 		the callback method where each object is passed when it is deserialized
     * @param <T>
     * 		the type of the objects being deserialized
     */
    private <T extends SelfSerializable> void readSerializableIterableWithSizeInternal(
            final int size,
            final boolean readClassId,
            final ThrowingFunction<Long, T, IOException> serializableConstructor,
            final Consumer<T> callback)
            throws IOException {

        if (serializableConstructor == null) {
            throw new IllegalArgumentException("serializableConstructor is null");
        }

        // return if size is zero while deserializing similar to serializing
        if (size == 0) {
            return;
        }

        final boolean allSameClass = readBoolean();

        final ValueReference<Long> classId = new ValueReference<>();
        final ValueReference<Integer> version = new ValueReference<>();

        for (int i = 0; i < size; i++) {
            final T next =
                    readNextSerializableIteration(allSameClass, readClassId, classId, version, serializableConstructor);
            callback.accept(next);
        }
    }

    /**
     * Helper method for {@link #readSerializableIterableWithSize(int, Consumer)}. Protected instead of
     * private to allow debug framework to intercept this method.
     *
     * @param allSameClass
     * 		true if the elements all have the same class
     * @param readClassId
     * 		if true then the class ID needs to be read, ignored if allSameClass is true
     * @param classId
     * 		the class ID if known, otherwise null
     * @param version
     * 		the version if known, otherwise ignored
     * @param serializableConstructor
     * 		given a class ID, returns a constructor for that class
     * @param <T>
     * 		the type of the elements in the sequence
     * @return true if the class ID has already been read
     */
    protected <T extends SelfSerializable> T readNextSerializableIteration(
            final boolean allSameClass,
            final boolean readClassId,
            final ValueReference<Long> classId,
            final ValueReference<Integer> version,
            final ThrowingFunction<Long, T, IOException> serializableConstructor)
            throws IOException {

        if (!allSameClass) {
            // if classes are different, we just read each object one by one
            return readSerializable(readClassId, serializableConstructor);
        }

        final boolean isNull = readBoolean();
        if (isNull) {
            return null;
        }

        if (version.getValue() == null) {
            // this is the first non-null member, so we read the ID and version
            if (readClassId) {
                classId.setValue(readLong());
            }
            version.setValue(readInt());
        }

        final T serializable = serializableConstructor.apply(classId.getValue());
        recordClassId(serializable.getClassId());
        recordClass(serializable);
        serializable.deserialize(this, version.getValue());
        return serializable;
    }

    /**
     * Read a list of serializable objects from the stream
     *
     * @param maxListSize
     * 		maximal number of object to read
     * @param <T>
     * 		the implementation of {@link SelfSerializable} used
     * @return A list of the instances of the class previously written
     * @throws IOException
     * 		thrown if any IO problems occur
     */
    public <T extends SelfSerializable> List<T> readSerializableList(int maxListSize) throws IOException {
        return readSerializableList(maxListSize, true, SerializableDataInputStream::registryConstructor);
    }

    /**
     * Read a list of serializable objects from the stream
     *
     * @param maxListSize
     * 		maximal number of object to read
     * @param readClassId
     * 		set to true if the class ID was written to the stream
     * @param serializableConstructor
     * 		the constructor to use when instantiating list elements
     * @param <T>
     * 		the implementation of {@link SelfSerializable} used
     * @return A list of the instances of the class previously written
     * @throws IOException
     * 		thrown if any IO problems occur
     */
    public <T extends SelfSerializable> List<T> readSerializableList(
            final int maxListSize, final boolean readClassId, final Supplier<T> serializableConstructor)
            throws IOException {
        CommonUtils.throwArgNull(serializableConstructor, "serializableConstructor");
        return readSerializableList(maxListSize, readClassId, id -> serializableConstructor.get());
    }

    /**
     * Read a list of serializable objects from the stream
     *
     * @param maxListSize
     * 		maximal number of object to read
     * @param readClassId
     * 		set to true if the class ID was written to the stream
     * @param serializableConstructor
     * 		a method that takes a class ID and returns a constructor
     * @param <T>
     * 		the implementation of {@link SelfSerializable} used
     * @return A list of the instances of the class previously written
     * @throws IOException
     * 		thrown if any IO problems occur
     */
    private <T extends SelfSerializable> List<T> readSerializableList(
            final int maxListSize,
            final boolean readClassId,
            final ThrowingFunction<Long, T, IOException> serializableConstructor)
            throws IOException {

        int length = readInt();
        if (length == NULL_LIST_ARRAY_LENGTH) {
            return null;
        }
        checkLengthLimit(length, maxListSize);

        // ArrayList is used by default, we can add support for different list types in the future
        List<T> list = new ArrayList<>(length);
        if (length == 0) {
            return list;
        }
        readSerializableIterableWithSizeInternal(length, readClassId, serializableConstructor, list::add);
        return list;
    }

    /**
     * Read an array of serializable objects from the stream.
     *
     * @param arrayConstructor
     * 		a method that returns an array of the requested size
     * @param maxListSize
     * 		maximal number of object should read
     * @param readClassId
     * 		set to true if the class ID was written to the stream
     * @param <T>
     * 		the implementation of {@link SelfSerializable} used
     * @return An array of the instances of the class previously written
     * @throws IOException
     * 		thrown if any IO problems occur
     */
    public <T extends SelfSerializable> T[] readSerializableArray(
            final IntFunction<T[]> arrayConstructor, final int maxListSize, final boolean readClassId)
            throws IOException {

        final List<T> list =
                readSerializableList(maxListSize, readClassId, SerializableDataInputStream::registryConstructor);
        if (list == null) {
            return null;
        }

        return list.toArray(arrayConstructor.apply(list.size()));
    }

    /**
     * Read an array of serializable objects from the stream.
     *
     * @param arrayConstructor
     * 		a method that returns an array of the requested size
     * @param maxListSize
     * 		maximal number of object should read
     * @param readClassId
     * 		set to true if the class ID was written to the stream
     * @param serializableConstructor
     * 		an object that returns new instances of the class
     * @param <T>
     * 		the implementation of {@link SelfSerializable} used
     * @return An array of the instances of the class previously written
     * @throws IOException
     * 		thrown if any IO problems occur
     */
    public <T extends SelfSerializable> T[] readSerializableArray(
            final IntFunction<T[]> arrayConstructor,
            final int maxListSize,
            final boolean readClassId,
            final Supplier<T> serializableConstructor)
            throws IOException {

        final List<T> list = readSerializableList(maxListSize, readClassId, id -> serializableConstructor.get());
        if (list == null) {
            return null;
        }
        return list.toArray(arrayConstructor.apply(list.size()));
    }

    /**
     * Looks up a constructor given a class ID.
     *
     * @param classId
     * 		a requested class ID
     * @param <T>
     * 		the type of the class
     * @return a constructor for the class
     * @throws ClassNotFoundException if the class ID is not registered
     */
    private static <T extends SelfSerializable> T registryConstructor(final long classId) throws IOException {
        final T rc = ConstructableRegistry.getInstance().createObject(classId);
        if (rc == null) {
            throw new ClassNotFoundException(classId);
        }
        return rc;
    }
}
