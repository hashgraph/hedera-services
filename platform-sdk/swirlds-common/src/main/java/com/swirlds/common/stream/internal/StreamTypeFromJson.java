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

package com.swirlds.common.stream.internal;

import com.swirlds.common.stream.StreamType;

/**
 * Contains properties related to certain stream file type.
 * The object can be built from a json file
 */
public final class StreamTypeFromJson implements StreamType {
    /**
     * description of the streamType, used for logging
     */
    private String description;
    /**
     * file name extension
     */
    private String extension;
    /**
     * file name extension of signature file
     */
    private String sigExtension;
    /**
     * Header which is written in the beginning of a stream file, before writing the Object Stream Version.
     */
    private int[] fileHeader;
    /**
     * Header which is written in the beginning of a stream signature file, before writing the Object Stream Signature
     * Version.
     */
    private byte[] sigFileHeader;

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public String getExtension() {
        return extension;
    }

    @Override
    public String getSigExtension() {
        return sigExtension;
    }

    @Override
    public int[] getFileHeader() {
        return fileHeader;
    }

    @Override
    public byte[] getSigFileHeader() {
        return sigFileHeader;
    }
}
