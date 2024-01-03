/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import com.swirlds.merkledb.collections.IndexedObject;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.file.Path;

/**
 * The aim for a DataFileReader is to facilitate fast highly concurrent random reading of items from
 * a data file. It is designed to be used concurrently from many threads.
 *
 * @param <D> Data item type
 */
public interface DataFileReader<D> extends AutoCloseable, Comparable<DataFileReader<D>>, IndexedObject {

    DataFileType getFileType();

    /**
     * Returns if this file is completed and ready to be compacted.
     *
     * @return if true the file is completed (read only and ready to compact)
     */
    boolean isFileCompleted();

    /**
     * Marks the reader as completed, so it can be included into future compactions. If the reader
     * is created for an existing file, it's usually marked as completed immediately. If the reader
     * is created for a new file, which is still being written in a different thread, it's marked as
     * completed right after the file is fully written and the writer is closed.
     */
    void setFileCompleted();

    /** Get the path to this data file */
    Path getPath();

    /**
     * Get file index, the index is an ordered integer identifying the file in a set of files
     *
     * @return this file's index
     */
    default int getIndex() {
        return getMetadata().getIndex();
    }

    /** Get the files metadata */
    DataFileMetadata getMetadata();

    /**
     * Create an iterator to iterate over the data items in this data file. It opens its own file
     * handle so can be used in a separate thread. It must therefore be closed when you are finished
     * with it.
     *
     * @return new data item iterator
     * @throws IOException if there was a problem creating a new DataFileIterator
     */
    DataFileIterator<D> createIterator() throws IOException;

    /**
     * Read data item bytes from file at dataLocation.
     *
     * @param dataLocation The file index combined with the offset for the starting block of the
     *     data in the file
     * @return Data item bytes
     * @throws IOException If there was a problem reading from data file
     * @throws ClosedChannelException if the data file was closed
     */
    // https://github.com/hashgraph/hedera-services/issues/8344: change return type to BufferedData
    Object readDataItemBytes(final long dataLocation) throws IOException;

    /**
     * Read data item from file at dataLocation and deserialize it to a Java object.
     *
     * @param dataLocation Data item location, which combines data file index and offset in the file
     * @return Deserialized data item
     * @throws IOException If there was a problem reading from data file
     * @throws ClosedChannelException if the data file was closed
     */
    D readDataItem(final long dataLocation) throws IOException;

    /**
     * Get the size of this file in bytes. This method should only be called for files available to
     * merging (compaction), i.e. after they are fully written.
     *
     * @return file size in bytes
     */
    long getSize();

    /**
     * Get if the DataFile is open for reading.
     *
     * @return True if file is open for reading
     */
    boolean isOpen();

    /**
     * Close this data file, it can not be used once closed.
     */
    @Override
    void close() throws IOException; // Override to throw IOException rather than generic Exception
}
