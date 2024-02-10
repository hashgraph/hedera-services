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

package com.swirlds.merkledb.files;

import static com.swirlds.merkledb.files.DataFileCommon.FOOTER_SIZE;
import static com.swirlds.merkledb.files.DataFileCompactor.INITIAL_COMPACTION_LEVEL;

import com.swirlds.base.utility.ToStringBuilder;
import com.swirlds.merkledb.utilities.MerkleDbFileUtils;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Objects;

/**
 * DataFile's metadata that is stored in the data file's footer
 */
@SuppressWarnings("unused")
// Future work: remove this class, once all DB files are converted to protobuf
// https://github.com/hashgraph/hedera-services/issues/8344
public final class DataFileMetadataJdb extends DataFileMetadata {

    /**
     * Create a new DataFileMetadata with complete set of data
     *
     * @param itemsCount           The number of data items the file contains
     * @param index                The file index, in a data file collection
     * @param creationDate         The creation data of this file, this is critical as it is used when merging two files
     *                             to know which files data is newer.
     * @param serializationVersion Serialization version for data stored in the file
     */
    public DataFileMetadataJdb(
            final long itemsCount,
            final int index,
            final Instant creationDate,
            final long serializationVersion,
            final int compactionLevel) {
        super(itemsCount, index, creationDate, serializationVersion, compactionLevel);
    }

    /**
     * Create a DataFileMetadata loading it from a existing file
     *
     * @param file The file to read metadata from
     * @throws IOException If there was a problem reading metadata footer from the file
     */
    public DataFileMetadataJdb(Path file) throws IOException {
        super(0, 0, null, 0, INITIAL_COMPACTION_LEVEL);
        try (final SeekableByteChannel channel = Files.newByteChannel(file, StandardOpenOption.READ)) {
            // read footer from end of file
            final ByteBuffer buf = ByteBuffer.allocate(FOOTER_SIZE);
            channel.position(channel.size() - FOOTER_SIZE);
            MerkleDbFileUtils.completelyRead(channel, buf);
            buf.rewind();
            // parse content
            buf.getInt(); // backwards compatibility: file format version
            buf.getInt(); // backwards compatibility: data item value size
            this.itemsCount = buf.getLong();
            this.index = buf.getInt();
            this.creationDate = Instant.ofEpochSecond(buf.getLong(), buf.getInt());
            this.compactionLevel = buf.get();
            this.serializationVersion = buf.getLong();
        }
    }

    /**
     * Get the metadata in the form of a one page 4k bytebuffer ready to write at the end of a file.
     *
     * @return ByteBuffer containing the metadata
     */
    public ByteBuffer getFooterForWriting() {
        ByteBuffer buf = ByteBuffer.allocate(FOOTER_SIZE);
        buf.putInt(0); // backwards compatibility: used to be file format version
        buf.putInt(0); // backwards compatibility: data item value size
        buf.putLong(this.itemsCount);
        buf.putInt(this.index);
        buf.putLong(this.creationDate.getEpochSecond());
        buf.putInt(this.creationDate.getNano());
        buf.put(this.compactionLevel);
        buf.putLong(this.serializationVersion);
        buf.rewind();
        return buf;
    }

    /**
     * Updates number of data items in the file. This method is called by {@link DataFileWriter}
     * right before the file is finished writing.
     */
    void setDataItemCount(final long dataItemCount) {
        this.itemsCount = dataItemCount;
    }

    /** toString for debugging */
    @Override
    public String toString() {
        return new ToStringBuilder(this)
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
