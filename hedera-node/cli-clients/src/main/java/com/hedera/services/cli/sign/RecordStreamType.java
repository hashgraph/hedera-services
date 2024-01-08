/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.cli.sign;

import static com.hedera.services.cli.sign.RecordStreamSigningUtils.SUPPORTED_STREAM_FILE_VERSION;

import com.swirlds.common.stream.StreamType;

/**
 * Contains properties related to RecordStream file type;
 * Its constructor is private. Users need to use the singleton to denote this type
 */
public final class RecordStreamType implements StreamType {
    /**
     * description of the streamType, used for logging
     */
    private static final String RECORD_STREAM_DESCRIPTION = "records";
    /**
     * file name extension
     */
    private static final String RECORD_STREAM_EXTENSION = "rcd";
    /**
     * file name extension with gz
     */
    private static final String RECORD_STREAM_GZ_EXTENSION = "rcd.gz";

    /**
     * file name extension of signature file
     */
    private static final String RECORD_STREAM_SIG_EXTENSION = "rcd_sig";
    /**
     * a singleton denotes EventStreamType
     */
    private static final RecordStreamType INSTANCE = new RecordStreamType();
    /**
     * Header which is written in the beginning of a stream file, before writing the Object Stream Version.
     * the int in fileHeader denotes version 6
     */
    private static final int[] RECORD_STREAM_FILE_HEADER = new int[] {SUPPORTED_STREAM_FILE_VERSION};
    /**
     * Header which is written in the beginning of a stream signature file, before writing the Object Stream Signature
     * Version.
     * the byte in sigFileHeader denotes version 6
     */
    private static final byte[] RECORD_STREAM_SIG_FILE_HEADER = new byte[] {SUPPORTED_STREAM_FILE_VERSION};

    private RecordStreamType() {}

    public static RecordStreamType getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return RECORD_STREAM_DESCRIPTION;
    }

    @Override
    public String getExtension() {
        return RECORD_STREAM_EXTENSION;
    }

    @Override
    public String getSigExtension() {
        return RECORD_STREAM_SIG_EXTENSION;
    }

    @Override
    public int[] getFileHeader() {
        return RECORD_STREAM_FILE_HEADER;
    }

    @Override
    public byte[] getSigFileHeader() {
        return RECORD_STREAM_SIG_FILE_HEADER;
    }

    public boolean isGzFile(final String fileName) {
        return fileName.endsWith(RECORD_STREAM_GZ_EXTENSION);
    }
}
