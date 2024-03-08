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

import static com.swirlds.merkledb.files.DataFileCollectionTestUtils.checkData;
import static com.swirlds.merkledb.files.DataFileCollectionTestUtils.getVariableSizeDataForI;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.io.utility.TemporaryFileBuilder;
import com.swirlds.merkledb.KeyRange;
import com.swirlds.merkledb.collections.LongListHeap;
import com.swirlds.merkledb.config.MerkleDbConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * A set of tests to check JDB (old binary format) to PBJ (new protobuf compatible format)
 * migration for data file collections.
 */
public class DataFileCollectionJdbToPbjTest {

    private static final MerkleDbConfig config = ConfigurationHolder.getConfigData(MerkleDbConfig.class);

    private static final String STORE_NAME = "test";

    private static final int VALUE_ADDITION = 1_000;

    private static long[] genValue(final FilesTestType testType, final int i) {
        return switch (testType) {
            case fixed, fixedComplexKey, variableComplexKey -> new long[] {i, i + VALUE_ADDITION};
            case variable -> getVariableSizeDataForI(i, VALUE_ADDITION);
        };
    }

    // Creates a new data file collection and generates the given number of files / entries.
    // If the folder contains data files, the collection will load them first
    private DataFileCollection<long[]> dataFileCollection(
            final Path dir,
            final FilesTestType testType,
            final int files,
            final AtomicInteger count,
            final int countInc,
            final LongListHeap index,
            final Predicate<Integer> usePbj)
            throws IOException {
        final DataFileCollection<long[]> fileCollection =
                new DataFileCollection<>(config, dir, STORE_NAME, testType.dataItemSerializer, null);
        for (int f = 0; f < files; f++) {
            fileCollection.startWriting(usePbj.test(f));
            int c = count.get();
            for (int i = c; i < c + countInc; i++) {
                long[] dataValue = genValue(testType, i);
                index.put(i, fileCollection.storeDataItem(dataValue));
            }
            final DataFileReader<long[]> newFile = fileCollection.endWriting(0, c + countInc);
            newFile.setFileCompleted();
            assertEquals(new KeyRange(0, c + countInc), fileCollection.getValidKeyRange(), "Range should be this");
            assertEquals(Files.size(newFile.getPath()), newFile.getSize());
            count.addAndGet(countInc);
        }
        return fileCollection;
    }

    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    @DisplayName("Write JDB files")
    void writeJdbTest(final FilesTestType testType) throws IOException {
        final Path dir = TemporaryFileBuilder.buildTemporaryDirectory("writeJdbTest");
        final Path testDir = dir.resolve(testType.name());

        final int FILES = 5;
        final int COUNT_INC = 100;
        final AtomicInteger count = new AtomicInteger(0);
        final LongListHeap index = new LongListHeap(1000);
        final DataFileCollection<long[]> fileCollection =
                dataFileCollection(testDir, testType, FILES, count, COUNT_INC, index, i -> false);
        assertEquals(FILES, fileCollection.getNumOfFiles());
        checkData(fileCollection, index, testType, 0, FILES * COUNT_INC, VALUE_ADDITION);

        final DataFileCollection<long[]> newCollection = new DataFileCollection<>(
                config, dir.resolve(testType.name()), "test", testType.dataItemSerializer, null);
        assertEquals(FILES, newCollection.getNumOfFiles());
        checkData(newCollection, index, testType, 0, FILES * COUNT_INC, VALUE_ADDITION);
    }

    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    @DisplayName("Write a mix of JDB and PBJ files")
    void writeMixTest(final FilesTestType testType) throws IOException {
        final Path dir = TemporaryFileBuilder.buildTemporaryDirectory("writeMixTest");
        final Path testDir = dir.resolve(testType.name());

        final int FILES = 10;
        final int COUNT_INC = 100;
        final AtomicInteger count = new AtomicInteger(0);
        final LongListHeap index = new LongListHeap(1000);
        final DataFileCollection<long[]> fileCollection =
                dataFileCollection(testDir, testType, FILES, count, COUNT_INC, index, i -> i % 2 == 0);
        assertEquals(FILES, fileCollection.getNumOfFiles());

        final DataFileCollection<long[]> newCollection = new DataFileCollection<>(
                config, dir.resolve(testType.name()), STORE_NAME, testType.dataItemSerializer, null);
        assertEquals(FILES, newCollection.getNumOfFiles());
        checkData(fileCollection, index, testType, 0, FILES * COUNT_INC, VALUE_ADDITION);
    }

    @ParameterizedTest
    @EnumSource(FilesTestType.class)
    @DisplayName("Write JDB files, then write PBJ files")
    void writeJdbWritePbjTest(final FilesTestType testType) throws IOException {
        final Path dir = TemporaryFileBuilder.buildTemporaryDirectory("writeJdbWritePbjTest");
        final Path testDir = dir.resolve(testType.name());

        final int FILES = 8;
        final AtomicInteger count = new AtomicInteger(0);
        int COUNT_INC = 10;
        final LongListHeap index = new LongListHeap(1000);
        final DataFileCollection<long[]> fileCollection =
                dataFileCollection(testDir, testType, FILES, count, COUNT_INC, index, i -> false);

        final DataFileCollection<long[]> fileCollection2 =
                dataFileCollection(testDir, testType, FILES, count, COUNT_INC, index, i -> true);

        final DataFileCollection<long[]> newCollection = new DataFileCollection<>(
                config, dir.resolve(testType.name()), STORE_NAME, testType.dataItemSerializer, null);
        assertEquals(FILES * 2, newCollection.getNumOfFiles());
        checkData(newCollection, index, testType, 0, FILES * COUNT_INC * 2, VALUE_ADDITION);
    }

    @ParameterizedTest
    @MethodSource("fileTypeAndBooleanParams")
    @DisplayName("Write a mix of JDB and PBJ files, merge")
    void writeMixMergeTest(final FilesTestType testType, final boolean usePbj)
            throws IOException, InterruptedException {
        final Path dir = TemporaryFileBuilder.buildTemporaryDirectory("writeMixMergeTest");
        final Path testDir = dir.resolve(testType.name());

        final int FILES = 15;
        final AtomicInteger count = new AtomicInteger(0);
        int COUNT_INC = 50;
        final LongListHeap index = new LongListHeap(1000);
        final DataFileCollection<long[]> fileCollection =
                dataFileCollection(testDir, testType, FILES, count, COUNT_INC, index, i -> i % 2 != 0);

        final DataFileCompactor<long[]> compactor =
                new DataFileCompactor<>(config, STORE_NAME, fileCollection, index, null, null, null, null);
        compactor.compactFiles(
                index, fileCollection.getAllCompletedFiles(), DataFileCompactor.INITIAL_COMPACTION_LEVEL + 1, usePbj);
        assertEquals(1, fileCollection.getNumOfFiles());
        checkData(fileCollection, index, testType, 0, FILES * COUNT_INC, VALUE_ADDITION);

        final DataFileCollection<long[]> newCollection = new DataFileCollection<>(
                config, dir.resolve(testType.name()), STORE_NAME, testType.dataItemSerializer, null);
        assertEquals(1, newCollection.getNumOfFiles());
        checkData(newCollection, index, testType, 0, FILES * COUNT_INC, VALUE_ADDITION);
    }

    @ParameterizedTest
    @MethodSource("fileTypeAndBooleanParams")
    @DisplayName("Write a mix of JDB and PBJ files, merge, write more PBJ files")
    void writeMixMergeWritePbjTest(final FilesTestType testType, final boolean usePbj)
            throws IOException, InterruptedException {
        final Path dir = TemporaryFileBuilder.buildTemporaryDirectory("writeMixMergeWritePbjTest");
        final Path testDir = dir.resolve(testType.name());

        final int FILES = 12;
        final AtomicInteger count = new AtomicInteger(0);
        int COUNT_INC = 20;
        final LongListHeap index = new LongListHeap(1000);
        final DataFileCollection<long[]> fileCollection =
                dataFileCollection(testDir, testType, FILES, count, COUNT_INC, index, i -> i % 2 != 0);

        final DataFileCompactor<long[]> compactor =
                new DataFileCompactor<>(config, STORE_NAME, fileCollection, index, null, null, null, null);
        compactor.compactFiles(
                index, fileCollection.getAllCompletedFiles(), DataFileCompactor.INITIAL_COMPACTION_LEVEL + 1, usePbj);
        assertEquals(1, fileCollection.getNumOfFiles());

        final DataFileCollection<long[]> fileCollection2 =
                dataFileCollection(testDir, testType, FILES, count, COUNT_INC, index, i -> true);
        assertEquals(1 + FILES, fileCollection2.getNumOfFiles());
        checkData(fileCollection2, index, testType, 0, FILES * COUNT_INC * 2, VALUE_ADDITION);

        final DataFileCollection<long[]> newCollection = new DataFileCollection<>(
                config, dir.resolve(testType.name()), STORE_NAME, testType.dataItemSerializer, null);
        assertEquals(1 + FILES, newCollection.getNumOfFiles());
        checkData(newCollection, index, testType, 0, FILES * COUNT_INC * 2, VALUE_ADDITION);
    }

    static Stream<Arguments> fileTypeAndBooleanParams() {
        final List<Arguments> args = new ArrayList<>();
        for (final FilesTestType filesTestType : FilesTestType.values()) {
            args.add(Arguments.of(filesTestType, false));
            args.add(Arguments.of(filesTestType, true));
        }
        return args.stream();
    }
}
