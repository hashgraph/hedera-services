package com.hedera.services.state.merkle.v3.files;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

import static com.hedera.services.state.merkle.v3.files.DataFileCommon.FOOTER_SIZE;

/**
 * DataFile's metadata that is stored in the data file's footer
 */
public final class DataFileMetadata {
    private final int fileFormatVersion;
    private final int dataItemValueSize;
    private final long dataItemCount;
    private final int index;
    private final Instant creationDate;
    private final long minimumValidKey;
    private final long maximumValidKey;
    private final boolean isMergeFile;

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
}
