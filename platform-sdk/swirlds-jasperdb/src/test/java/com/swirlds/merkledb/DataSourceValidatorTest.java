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

package com.swirlds.merkledb;

import static com.swirlds.common.test.fixtures.AssertionUtils.assertEventuallyEquals;
import static com.swirlds.merkledb.MerkleDbDataSourceTest.createDataSource;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.virtualmap.VirtualLongKey;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DataSourceValidatorTest {

    @TempDir
    private Path tempDir;

    private MerkleDbDataSource<VirtualLongKey, ExampleByteArrayVirtualValue> dataSource;
    private int count;
    private DataSourceValidator<VirtualLongKey, ExampleByteArrayVirtualValue> validator;

    @BeforeEach
    public void setup() throws IOException {
        count = 10_000;
        // check db count
        assertEventuallyEquals(
                0L, MerkleDbDataSource::getCountOfOpenDatabases, Duration.ofSeconds(1), "Expected no open dbs");
        // create db

        dataSource = createDataSource(tempDir, "createAndCheckInternalNodeHashes", TestType.fixed_fixed, count, 0);
        validator = new DataSourceValidator<>(dataSource);

        // check db count
        assertEventuallyEquals(
                1L, MerkleDbDataSource::getCountOfOpenDatabases, Duration.ofSeconds(1), "Expected only 1 db");
    }

    @Test
    void testValidateValidDataSource() throws IOException {
        // create some node hashes
        dataSource.saveRecords(
                count,
                count * 2L,
                IntStream.range(0, count).mapToObj(MerkleDbDataSourceTest::createVirtualInternalRecord),
                IntStream.range(count, count * 2 + 1)
                        .mapToObj(i -> TestType.fixed_fixed.dataType().createVirtualLeafRecord(i)),
                Stream.empty());

        assertTrue(validator.validate());
    }

    @Test
    void testValidateInvalidDataSource() throws IOException {
        // check db count
        // create some node hashes
        dataSource.saveRecords(
                count,
                count * 2L,
                IntStream.range(0, count).mapToObj(MerkleDbDataSourceTest::createVirtualInternalRecord),
                // leaves are missing
                Stream.empty(),
                Stream.empty());

        assertFalse(validator.validate());
    }

    @AfterEach
    public void cleanup() throws IOException {
        if (dataSource != null) {
            dataSource.close();
            // check db count
            assertEventuallyEquals(
                    0L, MerkleDbDataSource::getCountOfOpenDatabases, Duration.ofSeconds(1), "Expected no open dbs");
        }
    }
}
