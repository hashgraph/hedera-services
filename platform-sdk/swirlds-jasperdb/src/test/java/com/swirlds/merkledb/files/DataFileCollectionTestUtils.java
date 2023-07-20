package com.swirlds.merkledb.files;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
            final DataFileCollection<long[]> fileCollection, final LongListHeap storedOffsets,
            final FilesTestType testType, final int fromIndex, final int toIndexExclusive, final int valueAddition) {
        // now read back all the data and check all data
        for (int i = fromIndex; i < toIndexExclusive; i++) {
            // test read with index
            final long fi = i;
            final long[] dataItem2 = assertDoesNotThrow(
                    () -> fileCollection.readDataItemUsingIndex(storedOffsets, fi), "Read should not a exception.");
            checkDataItem(testType, valueAddition, dataItem2, i);
        }
    }

    static void checkDataItem(
            FilesTestType testType, final int valueAddition, final long[] dataItem, final int expectedKey) {
        assertNotNull(dataItem, "dataItem should not be null");
        switch (testType) {
            default:
            case fixed:
                assertEquals(2, dataItem.length, "unexpected length"); // size
                assertEquals(expectedKey, dataItem[0], "unexpected key"); // key
                assertEquals(expectedKey + valueAddition, dataItem[1], "unexpected value"); // value
                break;
            case variable:
                assertEquals(
                        Arrays.toString(getVariableSizeDataForI(expectedKey, valueAddition)),
                        Arrays.toString(dataItem),
                        "unexpected dataItem value");
                break;
        }
    }
}
