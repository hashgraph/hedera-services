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

package com.swirlds.jasperdb.files;

import static com.swirlds.jasperdb.files.DataFileCommon.FOOTER_SIZE;

import com.swirlds.jasperdb.utilities.JasperDBFileUtils;
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
public final class DataFileMetadata {
    /** The file format version, this is ready in case we need to change file format and support multiple versions. */
    private final int fileFormatVersion;
    /**
     * The data item value's size, if the file contains fixed size data items then this is the size in bytes of
     * those items. If the file contains variable size items then this is the constant VARIABLE_DATA_SIZE.
     */
    private final int dataItemValueSize;
    /** The number of data items the file contains */
    private final long dataItemCount;
    /** The file index, in a data file collection */
    private final int index;
    /**
     * The creation date of this file, this is critical as it is used when merging two files to know which files data
     * is newer.
     */
    private final Instant creationDate;
    /**
     * True if this file was created as part of a merge, false if it was fresh data. This can be used during merging to
     * select if we want to include previously merged files in a merging round or not.
     */
    private final boolean isMergeFile;
    /** Serialization version for data stored in the file */
    private final long serializationVersion;

    /**
     * Create a new DataFileMetadata with complete set of data
     *
     * @param fileFormatVersion
     * 		The file format version, this is ready in case we need to change file format and support
     * 		multiple versions.
     * @param dataItemValueSize
     * 		The data item value's size, if the file contains fixed size data items then this is the
     * 		size in bytes of those items. If the file contains variable size items then this is the
     * 		constant VARIABLE_DATA_SIZE.
     * @param dataItemCount
     * 		The number of data items the file contains
     * @param index
     * 		The file index, in a data file collection
     * @param creationDate
     * 		The creation data of this file, this is critical as it is used when merging two files to know
     * 		which files data is newer.
     * @param isMergeFile
     * 		True if this file was created as part of a merge, false if it was fresh data. This can be used
     * 		during merging to select if we want to include previously merged files in a merging round or
     * 		not.
     * @param serializationVersion
     * 		Serialization version for data stored in the file
     */
    public DataFileMetadata(
            final int fileFormatVersion,
            final int dataItemValueSize,
            final long dataItemCount,
            final int index,
            final Instant creationDate,
            final boolean isMergeFile,
            final long serializationVersion) {
        this.fileFormatVersion = fileFormatVersion;
        this.dataItemValueSize = dataItemValueSize;
        this.dataItemCount = dataItemCount;
        this.index = index;
        this.creationDate = creationDate;
        this.isMergeFile = isMergeFile;
        this.serializationVersion = serializationVersion;
    }

    /**
     * Create a DataFileMetadata loading it from a existing file
     *
     * @param file
     * 		The file to read metadata from
     * @throws IOException
     * 		If there was a problem reading metadata footer from the file
     */
    public DataFileMetadata(Path file) throws IOException {
        try (final SeekableByteChannel channel = Files.newByteChannel(file, StandardOpenOption.READ)) {
            // read footer from end of file
            final ByteBuffer buf = ByteBuffer.allocate(FOOTER_SIZE);
            channel.position(channel.size() - FOOTER_SIZE);
            JasperDBFileUtils.completelyRead(channel, buf);
            buf.rewind();
            // parse content
            this.fileFormatVersion = buf.getInt();
            this.dataItemValueSize = buf.getInt();
            this.dataItemCount = buf.getLong();
            this.index = buf.getInt();
            this.creationDate = Instant.ofEpochSecond(buf.getLong(), buf.getInt());
            this.isMergeFile = buf.get() == 1;
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
        buf.putInt(this.fileFormatVersion);
        buf.putInt(this.dataItemValueSize);
        buf.putLong(this.dataItemCount);
        buf.putInt(this.index);
        buf.putLong(this.creationDate.getEpochSecond());
        buf.putInt(this.creationDate.getNano());
        buf.put((byte) (this.isMergeFile ? 1 : 0));
        buf.putLong(this.serializationVersion);
        buf.rewind();
        return buf;
    }

    /**
     * Get the file format version, this is ready in case we need to change file format and support multiple versions.
     */
    public int getFileFormatVersion() {
        return fileFormatVersion;
    }

    /**
     * Get the data item value's size, if the file contains fixed size data items then this is the size in bytes of
     * those items. If the file contains variable size items then this is the constant VARIABLE_DATA_SIZE.
     */
    public int getDataItemValueSize() {
        return dataItemValueSize;
    }

    /**
     * Get if the file has variable size data
     */
    public boolean hasVariableSizeData() {
        return dataItemValueSize == DataFileCommon.VARIABLE_DATA_SIZE;
    }

    /**
     * Get the number of data items the file contains
     */
    public long getDataItemCount() {
        return dataItemCount;
    }

    /**
     * Get the files index, out of a set of data files
     */
    public int getIndex() {
        return index;
    }

    /**
     * Get the date the file was created in UTC
     */
    public Instant getCreationDate() {
        return creationDate;
    }

    /**
     * Get if the file is a merge file. True if this is a merge file, false if it is a new data file that has not been
     * merged.
     */
    public boolean isMergeFile() {
        return isMergeFile;
    }

    /**
     * Get the serialization version for data stored in this file
     */
    public long getSerializationVersion() {
        return serializationVersion;
    }

    /** toString for debugging */
    @Override
    public String toString() {
        return "DataFileMetadata{" + "fileFormatVersion="
                + fileFormatVersion + ", dataItemValueSize="
                + dataItemValueSize + ", dataItemCount="
                + dataItemCount + ", index="
                + index + ", creationDate="
                + creationDate + ", isMergeFile="
                + isMergeFile + ", serializationVersion="
                + serializationVersion + '}';
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
        return fileFormatVersion == that.fileFormatVersion
                && dataItemValueSize == that.dataItemValueSize
                && dataItemCount == that.dataItemCount
                && index == that.index
                && isMergeFile == that.isMergeFile
                && serializationVersion == that.serializationVersion
                && Objects.equals(this.creationDate, that.creationDate);
    }

    /**
     * hashCode for use when comparing in collections, based on all fields in the toString() output.
     */
    @Override
    public int hashCode() {
        return Objects.hash(
                fileFormatVersion,
                dataItemValueSize,
                dataItemCount,
                index,
                creationDate,
                isMergeFile,
                serializationVersion);
    }
}
