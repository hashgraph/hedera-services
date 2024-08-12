/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.swirlds.merkledb.collections.LongListHeap;
import java.util.Arrays;

public class DataFileCollectionTestUtils {

    /**
     * For tests, we want to have all different data sizes, so we use this function to choose how
     * many times to repeat the data value long
     */
    private static int getRepeatCountForKey(final long key) {
        return (int) (key % 20L);
    }

    /** Create an example variable sized data item with lengths of data from 1 to 20. */
    static long[] getVariableSizeDataForI(final int i, final int valueAddition) {
        final int repeatCount = getRepeatCountForKey(i);
        final long[] dataValue = new long[1 + repeatCount];
        dataValue[0] = i;
        for (int j = 1; j < dataValue.length; j++) {
            dataValue[j] = i + valueAddition;
        }
        return dataValue;
    }

    static void checkData(
            final DataFileCollection fileCollection,
            final LongListHeap storedOffsets,
            final FilesTestType testType,
            final int fromIndex,
            final int toIndexExclusive,
            final int valueAddition) {
        // now read back all the data and check all data
        for (int i = fromIndex; i < toIndexExclusive; i++) {
            // test read with index
            final long fi = i;
            final BufferedData dataItem = assertDoesNotThrow(
                    () -> fileCollection.readDataItemUsingIndex(storedOffsets, fi), "Read should not a exception.");
            checkDataItem(testType, valueAddition, dataItem, i);
        }
    }

    static void checkDataItem(
            FilesTestType testType, final int valueAddition, final BufferedData dataItem, final int expectedKey) {
        assertNotNull(dataItem, "dataItem should not be null");
        switch (testType) {
            default:
            case fixed:
                assertEquals(2 * Long.BYTES, dataItem.remaining(), "unexpected length"); // size
                assertEquals(expectedKey, dataItem.readLong(), "unexpected key"); // key
                assertEquals(expectedKey + valueAddition, dataItem.readLong(), "unexpected value"); // value
                break;
            case variable:
                final long[] dataItemLongs = new long[Math.toIntExact(dataItem.remaining() / Long.BYTES)];
                for (int i = 0; i < dataItemLongs.length; i++) {
                    dataItemLongs[i] = dataItem.readLong();
                }
                assertEquals(
                        Arrays.toString(getVariableSizeDataForI(expectedKey, valueAddition)),
                        Arrays.toString(dataItemLongs),
                        "unexpected dataItem value");
                break;
        }
    }
}
