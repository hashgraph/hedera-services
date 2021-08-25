package com.hedera.services.state.jasperdb.files;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

import static com.hedera.services.state.jasperdb.files.DataFileCommon.FOOTER_SIZE;

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
     * The creation data of this file, this is critical as it is used when merging two files to know which files data
     * is newer.
     */
    private final Instant creationDate;
    /**
     * The minimum valid key at the time this file was created. It is assumed valid keys are in a range from min to max
     * that changes over time. In most of our use cases this key is a leaf path so this is minLeafPath in that case.
     */
    private final long minimumValidKey;
    /** The maximum valid key at the time this file was created. */
    private final long maximumValidKey;
    /**
     * True if this file was created as part of a merge, false if it was fresh data. This can be used during merging to
     * select if we want to include previously merged files in a merging round or not.
     */
    private final boolean isMergeFile;

    /**
     * Create a new DataFileMetadata with complete set of data
     *
     * @param fileFormatVersion The file format version, this is ready in case we need to change file format and support
     *                          multiple versions.
     * @param dataItemValueSize The data item value's size, if the file contains fixed size data items then this is the
     *                          size in bytes of those items. If the file contains variable size items then this is the
     *                          constant VARIABLE_DATA_SIZE.
     * @param dataItemCount The number of data items the file contains
     * @param index The file index, in a data file collection
     * @param creationDate The creation data of this file, this is critical as it is used when merging two files to know
     *                     which files data is newer.
     * @param minimumValidKey The minimum valid key at the time this file was created. It is assumed valid keys are in a
     *                        range from min to max that changes over time. In most of our use cases this key is a leaf
     *                        path so this is minLeafPath in that case.
     * @param maximumValidKey The maximum valid key at the time this file was created.
     * @param isMergeFile True if this file was created as part of a merge, false if it was fresh data. This can be used
     *                    during merging to select if we want to include previously merged files in a merging round or
     *                    not.
     */
    public DataFileMetadata(int fileFormatVersion, int dataItemValueSize, long dataItemCount, int index,
                            Instant creationDate, long minimumValidKey, long maximumValidKey,
                            boolean isMergeFile) {
        this.fileFormatVersion = fileFormatVersion;
        this.dataItemValueSize = dataItemValueSize;
        this.dataItemCount = dataItemCount;
        this.index = index;
        this.creationDate = creationDate;
        this.minimumValidKey = minimumValidKey;
        this.maximumValidKey = maximumValidKey;
        this.isMergeFile = isMergeFile;
    }

    /**
     * Create a DataFileMetadata loading it from a existing file
     *
     * @param file The file to read metadata from
     * @throws IOException If there was a problem reading metadata footer from the file
     */
    public DataFileMetadata(Path file) throws IOException {
        try (SeekableByteChannel channel = Files.newByteChannel(file, StandardOpenOption.READ)) {
            // read footer from end of file
            ByteBuffer buf = ByteBuffer.allocate(FOOTER_SIZE);
            channel.position(channel.size()-FOOTER_SIZE);
            channel.read(buf);
            buf.rewind();
            // parse content
            this.fileFormatVersion = buf.getInt();
            this.dataItemValueSize = buf.getInt();
            this.dataItemCount = buf.getLong();
            this.index = buf.getInt();
            this.creationDate = Instant.ofEpochSecond(buf.getLong(), buf.getInt());
            this.minimumValidKey = buf.getLong();
            this.maximumValidKey = buf.getLong();
            this.isMergeFile = buf.get() == 1;
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
        buf.putLong(this.minimumValidKey);
        buf.putLong(this.maximumValidKey);
        buf.put((byte)(this.isMergeFile ? 1 : 0));
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
     * Get the minimum valid key value at the point the file was finished writing. This is useful during merge to
     * easily delete keys that are no longer valid.
     */
    public long getMinimumValidKey() {
        return minimumValidKey;
    }

    /**
     * Get the maximum valid key value at the point the file was finished writing. This is useful during merge to
     * easily delete keys that are no longer valid.
     */
    public long getMaximumValidKey() {
        return maximumValidKey;
    }

    /**
     * Get if the file is a merge file. True if this is a merge file, false if it is a new data file that has not been
     * merged.
     */
    public boolean isMergeFile() {
        return isMergeFile;
    }

    /** toString for debugging */
    @Override
    public String toString() {
        return "DataFileMetadata{" +
                "fileFormatVersion=" + fileFormatVersion +
                ", dataItemValueSize=" + dataItemValueSize +
                ", dataItemCount=" + dataItemCount +
                ", index=" + index +
                ", creationDate=" + creationDate +
                ", minimumValidKey=" + minimumValidKey +
                ", maximumValidKey=" + maximumValidKey +
                ", isMergeFile=" + isMergeFile +
                '}';
    }
}
