/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class DataFileMetadataTest {

    @Test
    void equalsIncorporatesAllFields() {
        final int fileFormatVersion = 1;
        final int dataItemValueSize = 2;
        final long dataItemCount = 3;
        final int index = 4;
        final Instant creationDate = Instant.ofEpochSecond(1_234_567L);
        final long serializationVersion = 7;

        final DataFileMetadata base = new DataFileMetadata(
                fileFormatVersion, dataItemValueSize, dataItemCount, index, creationDate, serializationVersion);
        final DataFileMetadata differentFormatVersion = new DataFileMetadata(
                fileFormatVersion + 1, dataItemValueSize, dataItemCount, index, creationDate, serializationVersion);
        final DataFileMetadata differentValueSize = new DataFileMetadata(
                fileFormatVersion, dataItemValueSize + 1, dataItemCount, index, creationDate, serializationVersion);
        final DataFileMetadata differentItemCount = new DataFileMetadata(
                fileFormatVersion, dataItemValueSize, dataItemCount + 1, index, creationDate, serializationVersion);
        final DataFileMetadata differentIndex = new DataFileMetadata(
                fileFormatVersion, dataItemValueSize, dataItemCount, index + 1, creationDate, serializationVersion);
        final DataFileMetadata differentCreationDate = new DataFileMetadata(
                fileFormatVersion,
                dataItemValueSize,
                dataItemCount,
                index,
                creationDate.plusSeconds(1),
                serializationVersion);
        final DataFileMetadata differentSerVersion = new DataFileMetadata(
                fileFormatVersion, dataItemValueSize, dataItemCount, index, creationDate, serializationVersion + 1);
        final DataFileMetadata otherButEqual = new DataFileMetadata(
                fileFormatVersion, dataItemValueSize, dataItemCount, index, creationDate, serializationVersion);

        assertEquals(base, otherButEqual, "Equivalent metadata are equal");
        assertNotEquals(base, differentFormatVersion, "Different format versions are unequal");
        assertNotEquals(base, differentValueSize, "Different value sizes are unequal");
        assertNotEquals(base, differentItemCount, "Different item counts are unequal");
        assertNotEquals(base, differentIndex, "Different indexes are unequal");
        assertNotEquals(base, differentCreationDate, "Different creation dates are unequal");
        assertNotEquals(base, differentSerVersion, "Different serialization versions are unequal");
        assertNotEquals(base, new Object(), "Radically different objects are unequal");
    }
}
