package com.hedera.services.state.merkle.v3.files;

import java.io.IOException;

/**
 * Merger for merging 2 or more data files into a new data file
 */
public class DataFileMerger {

    /**
     * Merge 2 or more data files into one
     *
     * @param newDataFile A new empty data file for us to write the merged data into
     * @param filesToMerge list of files to merge, must be two or more
     * @throws IOException if there was a problem merging
     */
    public static void merge(DataFile newDataFile, DataFile ... filesToMerge) throws IOException {

    }
}
