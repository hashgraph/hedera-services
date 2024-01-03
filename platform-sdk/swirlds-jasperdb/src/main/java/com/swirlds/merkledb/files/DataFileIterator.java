/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

import java.io.IOException;
import java.nio.file.Path;

/**
 * Iterator class for iterating over data items in a data file. It is designed to be used in a while(iter.next()){...}
 * loop and you can then read the data items info for current item with getDataItemsKey, getDataItemsDataLocation and
 * getDataItemData.
 *
 * <p>It is designed to be used from a single thread.
 *
 * @param <D> data item type
 * @see DataFileWriter for definition of file structure
 */
public interface DataFileIterator<D> extends AutoCloseable {

    /**
     * Get the path for the data file.
     */
    Path getPath();

    /**
     * Get the metadata for the data file.
     *
     * @return File's metadata
     */
    DataFileMetadata getMetadata();

    /**
     * Advance to the next dataItem.
     *
     * @return true if a dataItem was read or false if the end of the file has been reached.
     * @throws IOException
     * 		If there was a problem reading from file.
     */
    boolean next() throws IOException;

    /**
     * Get the current dataItems data. This is a shared buffer and must NOT be leaked from
     * the call site or modified directly.
     *
     * @return buffer containing the key and value data. This will return null if the iterator has
     * 		been closed, or if the iterator is in the before-first or after-last states.
     */
    D getDataItemData() throws IOException;

    /**
     * Get the data location (file + offset) for the current data item.
     *
     * @return current data item location
     */
    long getDataItemDataLocation();

    @Override
    void close() throws IOException; // Override to throw IOException rather than generic Exception
}
