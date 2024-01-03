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
 * Writer for creating a data file. A data file contains a number of data items. Each data item can
 * be variable or fixed size and is considered as a black box. All access to contents of the data
 * item is done via the DataItemSerializer.
 *
 * <p><b>This is designed to be used from a single thread.</b>
 *
 * @param <D> Data item type
 */
public interface DataFileWriter<D> {

    DataFileType getFileType();

    /** Get the path for the file being written. Useful when needing to get a reader to the file. */
    Path getPath();

    /**
     * Get file metadata for the written file.
     *
     * @return data file metadata
     */
    DataFileMetadata getMetadata();

    /**
     * Write a data item copied from another file like during merge. If this writer doesn't support
     * the provided raw data item type, this method should return null to indicate that the item
     * needs to be fully deserialized and then serialized to the target file rather than copied as
     * raw bytes.
     *
     * @param dataItemBytes a buffer containing the item's data
     * @return New data location in this file where it was written
     * @throws IllegalArgumentException If this writer doesn't support the given raw item bytes type
     * @throws IOException If there was a problem writing the data item
     */
    long writeCopiedDataItem(final Object dataItemBytes) throws IOException;

    /**
     * Store data item in file returning location it was stored at.
     *
     * @param dataItem the data item to write
     * @return the data location of written data in bytes
     * @throws IOException if there was a problem appending data to file
     */
    long storeDataItem(final D dataItem) throws IOException;

    /**
     * When you finished append to a new file, call this to seal the file and make it read only for
     * reading.
     *
     * @throws IOException if there was a problem sealing file or opening again as read only
     */
    void finishWriting() throws IOException;
}
