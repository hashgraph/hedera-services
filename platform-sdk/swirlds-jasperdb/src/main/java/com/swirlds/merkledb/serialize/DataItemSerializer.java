/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.merkledb.serialize;

import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public interface DataItemSerializer<D> extends BaseSerializer<D> {

    /**
     * Get the number of bytes used for data item header
     *
     * @return size of header in bytes
     */
    int getHeaderSize();

    /**
     * Deserialize data item header from the given byte buffer
     *
     * @param buffer
     * 		Buffer to read from
     * @return The read header
     */
    DataItemHeader deserializeHeader(ByteBuffer buffer);

    /**
     * Copy the serialized data item in dataItemData into the writingStream. Important if serializedVersion is not the
     * same as current serializedVersion then update the data to the latest serialization.
     *
     * @param serializedVersion
     * 		The serialized version of the data item in dataItemData
     * @param dataItemSize
     * 		The size in bytes of the data item dataItemData
     * @param dataItemData
     * 		Buffer containing complete data item including the data item header
     * @param writingStream
     * 		The stream to write data item out to
     * @return the number of bytes written, this could be the same as dataItemSize or bigger or smaller if
     * 		serialization version has changed.
     * @throws IOException
     * 		if there was a problem writing data item to stream or converting it
     */
    default int copyItem(
            final long serializedVersion,
            final int dataItemSize,
            final ByteBuffer dataItemData,
            final SerializableDataOutputStream writingStream)
            throws IOException {
        if (serializedVersion == getCurrentDataVersion()) {
            writingStream.write(dataItemData.array(), 0, dataItemSize);
        } else {
            // deserialize and reserialize to convert versions
            return serialize(deserialize(dataItemData, serializedVersion), writingStream);
        }
        return dataItemSize;
    }
}
