/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common.stream;

import java.io.File;

public interface StreamType {

    /**
     * get the description of the streamType, used for logging
     * @return the description of the streamType, used for logging
     */
    String getDescription();

    /**
     * get file name extension
     * @return file name extension
     */
    String getExtension();

    /**
     * get file name extension of signature file
     * @return file name extension of signature file
     */
    String getSigExtension();

    /**
     * get the header which is written in the beginning of a stream file,
     * before writing the Object Stream Version
     * @return stream file header
     */
    int[] getFileHeader();

    /**
     * get the header which is written in the beginning of a stream signature file,
     * before writing the Object Stream Signature Version
     * @return signature file header
     */
    byte[] getSigFileHeader();

    /**
     * check if the file with this name is a stream file of this type
     * @param fileName a file's name
     * @return whether the file with this name is a stream file of this type
     */
    default boolean isStreamFile(final String fileName) {
        return fileName.endsWith(getExtension());
    }

    /**
     * check if the given file is a stream file of this type
     * @param file a file
     * @return whether the file is a stream file of this type
     */
    default boolean isStreamFile(final File file) {
        return file != null && isStreamFile(file.getName());
    }

    /**
     * check if the given file is a signature file of this stream type
     * @param fileName a file's name
     * @return whether the file is a signature file of this stream type
     */
    default boolean isStreamSigFile(final String fileName) {
        return fileName.endsWith(getSigExtension());
    }

    /**
     * check if the given file is a signature file of this stream type
     * @param file a file
     * @return whether the file is a signature file of this stream type
     */
    default boolean isStreamSigFile(final File file) {
        return file != null && isStreamSigFile(file.getName());
    }
}
