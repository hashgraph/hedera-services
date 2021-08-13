package com.hedera.services.state.merkle;

import com.fasterxml.jackson.databind.util.ByteBufferBackedInputStream;
import com.fasterxml.jackson.databind.util.ByteBufferBackedOutputStream;
import com.hedera.services.state.serdes.IoReadingFunction;
import com.hedera.services.state.serdes.IoWritingConsumer;
import com.swirlds.common.CommonUtils;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.io.BadIOException;
import com.swirlds.common.io.ClassNotFoundException;
import com.swirlds.common.io.FunctionalSerialize;
import com.swirlds.common.io.InvalidVersionException;
import com.swirlds.common.io.NotSerializableException;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.io.SerializableDet;
import com.swirlds.common.io.SerializableStreamConstants;
import com.swirlds.virtualmap.ByteBufferSelfSerializable;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class SerializationHelper {
    static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;
    static final int NULL_VERSION = Integer.MIN_VALUE;  // SerializableStreamConstants.NULL_VERSION not visible

    public static void writeNullableString(ByteBuffer buffer, String s) throws IOException {
        if (s == null) {
            buffer.put((byte) 0);
        } else {
            buffer.put((byte) 1);
            writeNormalisedString(buffer, s);
        }
    }

    public static void writeNormalisedString(ByteBuffer buffer, String s) throws IOException {
        writeByteArray(buffer, CommonUtils.getNormalisedStringBytes(s));
    }

    public static String readNormalisedString(ByteBuffer buffer, int maxLength) throws IOException {
        return new String(readByteArray(buffer, maxLength), DEFAULT_CHARSET);
    }

    public static void writeSerializable(ByteBuffer buffer, SelfSerializable s, boolean writeClassId) throws IOException {
        writeSerializable(buffer, s, writeClassId, s);
    }

    public static void writeSerializable(
            ByteBuffer buffer,
            SelfSerializable s,
            boolean writeClassId,
            FunctionalSerialize serializeMethod
    ) throws IOException {
        if (s == null) {
            if (writeClassId)
                buffer.putLong(SerializableStreamConstants.NULL_CLASS_ID);
            else
                buffer.putLong(NULL_VERSION);
        } else {
            writeClassIdVersion(buffer, s, writeClassId);
            writeSerializable(buffer, serializeMethod);
            writeFlag(buffer, s.getClassId());
        }
    }

    public static void writeNullableSerializable(ByteBuffer buffer, SelfSerializable s) throws IOException {
        if (s == null) {
            buffer.put((byte) 0);
        } else {
            buffer.put((byte) 1);
            writeSerializable(buffer, s, true);
        }
    }

    public static void write(ByteBuffer buffer, ByteBufferSelfSerializable s) throws IOException {
        buffer.putInt(s.getVersion());
        s.serialize(buffer);
    }

    public static void writeNullable(ByteBuffer buffer, ByteBufferSelfSerializable s) throws IOException {
        if (s == null) {
            buffer.put((byte) 0);
        } else {
            buffer.put((byte) 1);
            buffer.putInt(s.getVersion());
            s.serialize(buffer);
        }
    }

    public static <T> void writeSerializable(ByteBuffer buffer, T data, IoWritingConsumer<T> writer) throws IOException {
        try(ByteBufferBackedOutputStream bos = new ByteBufferBackedOutputStream(buffer)) {
            try (SerializableDataOutputStream dos = new SerializableDataOutputStream(bos)) {
                writer.write(data, dos);
                dos.flush();
                bos.flush();
            }
        }
    }

    // TODO: DomainSerdes (serialization/deserialization) has a set of serialization methods that are very similar
    // to what is present in SerializationDataInputStream + SerializableDataOutputStream. We should have one single
    // way of serializing to/from bytes.
    public static <T> void writeNullableSerializable(
            ByteBuffer buffer,
            T data,
            IoWritingConsumer<T> writer
    ) throws IOException {
        if (data == null) {
            buffer.put((byte) 0);
        } else {
            buffer.put((byte) 1);
            writeSerializable(buffer, data, writer);
        }
    }

    public static void writeSerializable(ByteBuffer buffer, FunctionalSerialize s) throws IOException {
        try(ByteBufferBackedOutputStream bos = new ByteBufferBackedOutputStream(buffer)) {
            try (SerializableDataOutputStream dos = new SerializableDataOutputStream(bos)) {
                s.serialize(dos);
                dos.flush();
                bos.flush();
            }
        }
    }

    public static <T extends SelfSerializable> void writeSerializableList(
            ByteBuffer buffer,
            List<T> list,
            boolean writeClassId,
            boolean allSameClass
    ) throws IOException {
        if (list == null) {
            buffer.putInt(-1);
        } else {
            writeSerializableIterableWithSize(buffer, list.iterator(), list.size(), writeClassId, allSameClass);
        }
    }

    public static <T extends SelfSerializable> void writeSerializableIterableWithSize(
            ByteBuffer buffer,
            Iterator<T> iterator,
            int size,
            boolean writeClassId,
            boolean allSameClass
    ) throws IOException {
        buffer.putInt(size);
        if (size != 0) {
            buffer.put((byte) (allSameClass ? 1 : 0));
            boolean classIdVersionWritten = false;

            while(iterator.hasNext()) {
                SelfSerializable serializable = (SelfSerializable) iterator.next();
                if (!allSameClass) {
                    writeSerializable(buffer, serializable, writeClassId);
                } else if (serializable == null) {
                    buffer.put((byte) 1);
                } else {
                    buffer.put((byte) 0);
                    if (!classIdVersionWritten) {
                        writeClassIdVersion(buffer, serializable, writeClassId);
                        classIdVersionWritten = true;
                    }

                    writeSerializable(buffer, serializable);
                }
            }
        }
    }

    public static void writeLongArray(ByteBuffer buffer, long[] data) throws IOException {
        if (data == null) {
            buffer.putInt(-1);
        } else {
            buffer.putInt(data.length);
            for(int i = 0; i < data.length; ++i) {
                buffer.putLong(data[i]);
            }
        }
    }

    public static String readNullableString(ByteBuffer buffer, int maxLen) throws IOException {
        if (buffer.get() == 0)
            return null;

        return readNormalisedString(buffer, maxLen);
    }

    public static <T extends SelfSerializable> T readSerializable(ByteBuffer buffer) throws IOException {
        return readSerializable(buffer, true, SerializationHelper::registryConstructor);
    }

    public static <T extends SelfSerializable> T readSerializable(
            ByteBuffer buffer,
            boolean readClassId,
            Supplier<T> serializableConstructor
    ) throws IOException {
        CommonUtils.throwArgNull(serializableConstructor, "serializableConstructor");
        return readSerializable(buffer, readClassId, (id) -> {
            return (T) serializableConstructor.get();
        });
    }

    static <T extends SelfSerializable> T readSerializable(
            ByteBuffer buffer,
            boolean readClassId,
            Function<Long, T> serializableConstructor
    ) throws IOException {
        Long classId = null;
        if (readClassId) {
            classId = buffer.getLong();
            if (classId == SerializableStreamConstants.NULL_CLASS_ID) {
                return null;
            }
        }

        int version = buffer.getInt();
        if (version == NULL_VERSION) {
            return null;
        } else {
            T serializable = (T) serializableConstructor.apply(classId);
            validateVersion(serializable, version);
            readIntoSerializable(buffer, serializable, version);
            validateFlag(serializable);
            return serializable;
        }
    }

    public static void readIntoSerializable(ByteBuffer buffer, SelfSerializable s, int version) throws IOException {
        try(ByteBufferBackedInputStream bis = new ByteBufferBackedInputStream(buffer)) {
            try(SerializableDataInputStream dis = new SerializableDataInputStream(bis)) {
                s.deserialize(dis, version);
            }
        }
    }

    public static <T> T readSerializable(ByteBuffer buffer, IoReadingFunction<T> reader) throws IOException {
        try(ByteBufferBackedInputStream bis = new ByteBufferBackedInputStream(buffer)) {
            try(SerializableDataInputStream dis = new SerializableDataInputStream(bis)) {
                return reader.read(dis);
            }
        }
    }

    public static <T> T readNullableSerializable(ByteBuffer buffer, IoReadingFunction<T> reader) throws IOException {
        return buffer.get() != 0 ? readSerializable(buffer, reader) : null;
    }

    public static <T extends SelfSerializable> T readNullableSerializable(ByteBuffer buffer) throws IOException {
        return buffer.get() != 0 ? readSerializable(buffer) : null;
    }

    public static <T extends ByteBufferSelfSerializable> T readNullable(
            ByteBuffer buffer,
            Supplier<T> constructor
    ) throws IOException {
        if (buffer.get() == 0) {
            return null;
        }
        int version = buffer.getInt();
        T value = constructor.get();
        value.deserialize(buffer, version);
        return value;
    }

    public static <T extends ByteBufferSelfSerializable> T read(
            ByteBuffer buffer,
            Supplier<T> constructor
    ) throws IOException {
        int version = buffer.getInt();
        T value = constructor.get();
        value.deserialize(buffer, version);
        return value;
    }

    public static void readInto(ByteBuffer buffer, ByteBufferSelfSerializable s) throws IOException {
        int version = buffer.getInt();
        s.deserialize(buffer, version);
    }

    public static <T extends SelfSerializable> List<T> readSerializableList(
            ByteBuffer buffer,
            int maxListSize
    ) throws IOException {
        return readSerializableList(buffer, maxListSize, true, SerializationHelper::registryConstructor);
    }

    public static <T extends SelfSerializable> List<T> readSerializableList(
            ByteBuffer buffer,
            int maxListSize,
            boolean readClassId,
            Supplier<T> serializableConstructor
    ) throws IOException {
        CommonUtils.throwArgNull(serializableConstructor, "serializableConstructor");
        return readSerializableList(buffer, maxListSize, readClassId, (id) -> serializableConstructor.get());
    }

    static <T extends SelfSerializable> List<T> readSerializableList(
            ByteBuffer buffer,
            int maxListSize,
            boolean readClassId,
            Function<Long, T> serializableConstructor
    ) throws IOException {
        int length = buffer.getInt();
        if (length == -1) {
            return null;
        } else {
            checkLengthLimit(length, maxListSize);
            List<T> list = new ArrayList(length);
            if (length == 0) {
                return list;
            } else {
                Objects.requireNonNull(list);
                readSerializableIterableWithSize(buffer, length, readClassId, serializableConstructor, list::add);
                return list;
            }
        }
    }

    static <T extends SelfSerializable> void readSerializableIterableWithSize(
            ByteBuffer buffer,
            int size,
            boolean readClassId,
            Function<Long, T> serializableConstructor,
            Consumer<T> callback
    ) throws IOException {
        if (serializableConstructor == null) {
            throw new IllegalArgumentException("serializableConstructor is null");
        } else if (size != 0) {
            boolean allSameClass = buffer.get() != 0;
            boolean classIdVersionRead = false;
            Integer version = null;
            Long classId = null;

            for(int i = 0; i < size; ++i) {
                if (!allSameClass) {
                    callback.accept(readSerializable(buffer, readClassId, serializableConstructor));
                } else {
                    boolean isNull = buffer.get() != 0;
                    if (isNull) {
                        callback.accept(null);
                    } else {
                        if (!classIdVersionRead) {
                            if (readClassId) {
                                classId = buffer.getLong();
                            }

                            version = buffer.getInt();
                            classIdVersionRead = true;
                        }

                        T serializable = serializableConstructor.apply(classId);
                        readIntoSerializable(buffer, serializable, version);
                        callback.accept(serializable);
                    }
                }
            }
        }
    }

    public static long[] readLongArray(ByteBuffer buffer, int maxLength) throws IOException {
        int len = buffer.getInt();
        if (len == -1) {
            return null;
        } else {
            checkLengthLimit(len, maxLength);
            long[] data = new long[len];

            for(int i = 0; i < len; ++i) {
                data[i] = buffer.getLong();
            }

            return data;
        }
    }

    public static void writeClassIdVersion(ByteBuffer buffer, SelfSerializable s, boolean writeClassId) throws IOException {
        if (writeClassId) {
            buffer.putLong(s.getClassId());
        }

        buffer.putInt(s.getVersion());
    }

    public static void writeByteArray(ByteBuffer buffer, byte[] data, boolean writeChecksum) throws IOException {
        if (data == null) {
            buffer.putInt(-1);
        } else {
            buffer.putInt(data.length);
            if (writeChecksum) {
                buffer.putInt(101 - data.length);
            }

            buffer.put(data);
        }
    }

    public static void writeByteArray(ByteBuffer buffer, byte[] data) throws IOException {
        writeByteArray(buffer, data, false);
    }

    public static byte[] readByteArray(ByteBuffer buffer, int maxLength, boolean readChecksum) throws IOException {
        int len = buffer.getInt();
        if (len < 0) {
            return null;
        } else {
            if (readChecksum) {
                int checksum = buffer.getInt();
                if (checksum != 101 - len) {
                    throw new BadIOException("SerializableDataInputStream tried to create array of length " + len + " with wrong checksum.");
                }
            }

            checkLengthLimit(len, maxLength);
            byte[] bytes = new byte[len];
            buffer.get(bytes);
            return bytes;
        }
    }

    public static byte[] readByteArray(ByteBuffer buffer, int maxLength) throws IOException {
        return readByteArray(buffer, maxLength, false);
    }

    /**
     * The SerializableDataOutputStream.writeFlag method will write the specified classId to the
     * output stream only if platform is set to debug mode. For now skip this step until platform
     * is refactored to take VirtualMap.
     */
    public static void writeFlag(ByteBuffer buffer, long classId) throws IOException { }

    /**
     * The SerializableDataInputStream.validateFlag method will check the classId in the input
     * stream only if the platform is set to debug mode. For now skip this step until platform
     * is refactored to take VirtualMap.
     */
    public static void validateFlag(SelfSerializable object) throws IOException { }

    public static void checkLengthLimit(int length, int maxLength) throws IOException {
        if (length > maxLength) {
            throw new IOException(String.format("The input stream provided a length of %d for the list/array which exceeds the maxLength of %d", length, maxLength));
        }
    }

    /**
     * Copied from SerializableDataInputStream.registryConstructor since that method is private.
     */
    static <T extends SelfSerializable> T registryConstructor(long classId) throws NotSerializableException {
        T rc = (T) ConstructableRegistry.createObject(classId);
        if (rc == null) {
            throw new ClassNotFoundException(classId);
        } else {
            return rc;
        }
    }

    /**
     * Copied from SerializableDataInputStream.validateVersion since that method is private.
     */
    static void validateVersion(SerializableDet object, int version) {
        if (version < object.getMinimumSupportedVersion() || version > object.getVersion()) {
            throw new InvalidVersionException(version, object);
        }
    }
}
