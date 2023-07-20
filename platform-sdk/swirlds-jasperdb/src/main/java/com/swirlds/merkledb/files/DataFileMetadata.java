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

package com.swirlds.merkledb.files;

import static com.swirlds.merkledb.files.DataFileCommon.FIELD_DATAFILE_CREATION_NANOS;
import static com.swirlds.merkledb.files.DataFileCommon.FIELD_DATAFILE_CREATION_SECONDS;
import static com.swirlds.merkledb.files.DataFileCommon.FIELD_DATAFILE_INDEX;
import static com.swirlds.merkledb.files.DataFileCommon.FIELD_DATAFILE_ITEMS_COUNT;
import static com.swirlds.merkledb.files.DataFileCommon.FIELD_DATAFILE_ITEM_VERSION;
import static com.swirlds.merkledb.utilities.ProtoUtils.WIRE_TYPE_FIXED_64_BIT;
import static com.swirlds.merkledb.utilities.ProtoUtils.WIRE_TYPE_VARINT;
import static org.apache.commons.lang3.builder.ToStringStyle.SHORT_PREFIX_STYLE;

import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.merkledb.utilities.ProtoUtils;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Objects;
import org.apache.commons.lang3.builder.ToStringBuilder;

/**
 * DataFile's metadata that is stored in the data file's footer
 */
@SuppressWarnings("unused")
// Future work: make this class final, once DataFileMetadataJdb is dropped
public class DataFileMetadata {

    /** The file index, in a data file collection */
    // Future work: make it private final, once this class is final again
    protected int index;

    /** The creation date of this file */
    // Future work: make it private final, once this class is final again
    protected Instant creationDate;

    /**
     * The number of data items the file contains. When metadata is loaded from a file, the number
     * of items is read directly from there. When metadata is created by {@link DataFileWriter} for
     * new files during flushes or compactions, this field is set to 0 initially and then updated
     * right before the file is finished writing. For such new files, no code needs their metadata
     * until they are fully written, so wrong (zero) item count shouldn't be an issue.
     */
    // Future work: make it private, once this class is final again
    protected volatile long itemsCount;

    /** Serialization version for data stored in the file */
    // Future work: make it private final, once this class is final again
    protected long serializationVersion;

    /** Header (metadata) size, in bytes */
    private final int headerSize;

    // Set in writeTo()
    private long dataItemCountHeaderOffset = 0;

    /**
     * Create a new DataFileMetadata with complete set of data
     *
     * @param itemsCount The number of data items the file contains
     * @param index The file index, in a data file collection
     * @param creationDate The creation data of this file, this is critical as it is used when
     *     merging two files to know which files data is newer.
     * @param serializationVersion Serialization version for data stored in the file
     */
    public DataFileMetadata(
            final long itemsCount,
            final int index,
            final Instant creationDate,
            final long serializationVersion) {
        this.itemsCount = itemsCount;
        this.index = index;
        this.creationDate = creationDate;
        this.serializationVersion = serializationVersion;
        headerSize = calculateHeaderSize();
    }

    /**
     * Create a DataFileMetadata loading it from a existing file
     *
     * @param file The file to read metadata from
     * @throws IOException If there was a problem reading metadata footer from the file
     */
    public DataFileMetadata(Path file) throws IOException {
        MappedByteBuffer readingMmap = null;
        try (final FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            readingMmap = channel.map(MapMode.READ_ONLY, 0, Math.min(1024, channel.size()));
            final BufferedData readingHeaderPbjData = BufferedData.wrap(readingMmap);
            this.index = ProtoUtils.readProtoField(
                    readingHeaderPbjData, FIELD_DATAFILE_INDEX,
                    o -> o.readVarInt(false));
            final long creationDataSeconds = ProtoUtils.readProtoField(
                    readingHeaderPbjData, FIELD_DATAFILE_CREATION_SECONDS,
                    o -> o.readVarLong(false));
            final int creationDataNanos = ProtoUtils.readProtoField(
                    readingHeaderPbjData, FIELD_DATAFILE_CREATION_NANOS,
                    o -> o.readVarInt(false));
            this.creationDate = Instant.ofEpochSecond(creationDataSeconds, creationDataNanos);
            this.itemsCount = ProtoUtils.readProtoField(
                    readingHeaderPbjData, FIELD_DATAFILE_ITEMS_COUNT,
                    o -> o.readLong(ByteOrder.LITTLE_ENDIAN));
            this.serializationVersion = ProtoUtils.readProtoField(
                    readingHeaderPbjData, FIELD_DATAFILE_ITEM_VERSION,
                    o -> o.readVarLong(false));
            headerSize = calculateHeaderSize();
        } finally {
            if (readingMmap != null) {
                DataFileCommon.closeMmapBuffer(readingMmap);
            }
        }
    }

    // Future work: make it private once DataFileMetadataJdb is dropped
    protected int calculateHeaderSize() {
//        return ProtoWriterTools.sizeOfInteger(FIELD_DATAFILE_INDEX, index) +
        return ProtoUtils.sizeOfTag(FIELD_DATAFILE_INDEX, WIRE_TYPE_VARINT) +
                ProtoUtils.sizeOfUnsignedVarInt32(index) +
//                ProtoWriterTools.sizeOfLong(FIELD_DATAFILE_CREATION_SECONDS, creationDate.getEpochSecond()) +
                ProtoUtils.sizeOfTag(FIELD_DATAFILE_CREATION_SECONDS, WIRE_TYPE_VARINT) +
                ProtoUtils.sizeOfUnsignedVarInt64(creationDate.getEpochSecond()) +
//                ProtoWriterTools.sizeOfInteger(FIELD_DATAFILE_CREATION_NANOS, creationDate.getNano()) +
                ProtoUtils.sizeOfTag(FIELD_DATAFILE_CREATION_NANOS, WIRE_TYPE_VARINT) +
                ProtoUtils.sizeOfUnsignedVarInt32(creationDate.getNano()) +
//                ProtoWriterTools.sizeOfLong(FIELD_DATAFILE_ITEMS_COUNT, itemsCount) +
                ProtoUtils.sizeOfTag(FIELD_DATAFILE_ITEMS_COUNT, WIRE_TYPE_FIXED_64_BIT) +
                Long.BYTES +
//                ProtoWriterTools.sizeOfLong(FIELD_DATAFILE_ITEM_VERSION, serializationVersion);
                ProtoUtils.sizeOfTag(FIELD_DATAFILE_ITEM_VERSION, WIRE_TYPE_VARINT) +
                ProtoUtils.sizeOfUnsignedVarInt64(serializationVersion);
    }

    void writeTo(final BufferedData out) throws IOException {
//        ProtoWriterTools.writeInteger(out, FIELD_DATAFILE_INDEX, getIndex());
        ProtoUtils.writeTag(out, FIELD_DATAFILE_INDEX);
        out.writeVarInt(getIndex(), false);
        final Instant creationInstant = getCreationDate();
//        ProtoWriterTools.writeLong(out, FIELD_DATAFILE_CREATION_SECONDS, creationInstant.getEpochSecond());
        ProtoUtils.writeTag(out, FIELD_DATAFILE_CREATION_SECONDS);
        out.writeVarLong(creationInstant.getEpochSecond(), false);
//        ProtoWriterTools.writeInteger(out, FIELD_DATAFILE_CREATION_NANOS, creationInstant.getNano());
        ProtoUtils.writeTag(out, FIELD_DATAFILE_CREATION_NANOS);
        out.writeVarInt(creationInstant.getNano(), false);
        dataItemCountHeaderOffset = out.position();
//        ProtoWriterTools.writeLong(out, FIELD_DATAFILE_ITEMS_COUNT, 0); // will be updated later
        ProtoUtils.writeTag(out, FIELD_DATAFILE_ITEMS_COUNT);
        out.writeLong(0, ByteOrder.LITTLE_ENDIAN); // will be updated later
//        ProtoWriterTools.writeLong(out, FIELD_DATAFILE_ITEM_VERSION, getSerializationVersion());
        ProtoUtils.writeTag(out, FIELD_DATAFILE_ITEM_VERSION);
        out.writeVarLong(getSerializationVersion(), false);
    }

    /**
     * Get the number of data items the file contains. If this method is called before the
     * corresponding file is completely written by {@link DataFileWriter}, the return value is 0.
     */
    public long getDataItemCount() {
        return itemsCount;
    }

    /**
     * Updates number of data items in the file. This method is called by {@link DataFileWriter}
     * right before the file is finished writing.
     */
    void updateDataItemCount(final BufferedData out, final long count) throws IOException {
        this.itemsCount = count;
        assert dataItemCountHeaderOffset != 0;
        out.position(dataItemCountHeaderOffset);
//        ProtoWriterTools.writeLong(out, FIELD_DATAFILE_ITEMS_COUNT, count);
        ProtoUtils.writeTag(out, FIELD_DATAFILE_ITEMS_COUNT);
        out.writeLong(count, ByteOrder.LITTLE_ENDIAN);
    }

    /** Get the files index, out of a set of data files */
    public int getIndex() {
        return index;
    }

    /** Get the date the file was created in UTC */
    public Instant getCreationDate() {
        return creationDate;
    }

    /** Get the serialization version for data stored in this file */
    public long getSerializationVersion() {
        return serializationVersion;
    }

    /** Get header (metadata) size, in bytes */
    public int getHeaderSize() {
        return headerSize;
    }

    /** toString for debugging */
    @Override
    public String toString() {
        return new ToStringBuilder(this, SHORT_PREFIX_STYLE)
                .append("itemsCount", itemsCount)
                .append("index", index)
                .append("creationDate", creationDate)
                .append("serializationVersion", serializationVersion)
                .toString();
    }

    /**
     * Equals for use when comparing in collections, based on all fields in the toString() output.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DataFileMetadata that = (DataFileMetadata) o;
        return itemsCount == that.itemsCount
                && index == that.index
                && serializationVersion == that.serializationVersion
                && Objects.equals(this.creationDate, that.creationDate);
    }

    /**
     * hashCode for use when comparing in collections, based on all fields in the toString() output.
     */
    @Override
    public int hashCode() {
        return Objects.hash(itemsCount, index, creationDate, serializationVersion);
    }
}
