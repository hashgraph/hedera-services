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

/**
 * Contains properties related to EventStream file type;
 * Its constructor is private. Users need to use the singleton to denote this type
 */
public final class EventStreamType implements StreamType {
    /**
     * description of the streamType, used for logging
     */
    public static final String EVENT_DESCRIPTION = "events";
    /**
     * file name extension
     */
    public static final String EVENT_EXTENSION = "evts";
    /**
     * file name extension of signature file
     */
    public static final String EVENT_SIG_EXTENSION = "evts_sig";
    /**
     * a singleton denotes EventStreamType
     */
    private static final EventStreamType INSTANCE = new EventStreamType();
    /**
     * Header which is written in the beginning of a stream file, before writing the Object Stream Version.
     * the int in fileHeader denotes version 5
     */
    private static final int[] EVENT_FILE_HEADER = new int[] {5};
    /**
     * Header which is written in the beginning of a stream signature file, before writing the Object Stream Signature
     * Version.
     * the byte in sigFileHeader denotes version 5
     */
    private static final byte[] EVENT_SIG_FILE_HEADER = new byte[] {5};

    private EventStreamType() {}

    public static EventStreamType getInstance() {
        return INSTANCE;
    }

    @Override
    public String getDescription() {
        return EVENT_DESCRIPTION;
    }

    @Override
    public String getExtension() {
        return EVENT_EXTENSION;
    }

    @Override
    public String getSigExtension() {
        return EVENT_SIG_EXTENSION;
    }

    @Override
    public int[] getFileHeader() {
        return EVENT_FILE_HEADER;
    }

    @Override
    public byte[] getSigFileHeader() {
        return EVENT_SIG_FILE_HEADER;
    }
}
