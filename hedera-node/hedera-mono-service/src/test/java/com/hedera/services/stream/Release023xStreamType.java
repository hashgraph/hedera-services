/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.stream;

public enum Release023xStreamType implements RecordStreamType {
    RELEASE_023x_STREAM_TYPE;

    private static final int[] RELEASE_023x_FILE_HEADER = new int[] {5, 0, 23, 0};

    @Override
    public int[] getFileHeader() {
        return RELEASE_023x_FILE_HEADER;
    }

    @Override
    public byte[] getSigFileHeader() {
        return new byte[] {(byte) RELEASE_023x_FILE_HEADER[0]};
    }
}
