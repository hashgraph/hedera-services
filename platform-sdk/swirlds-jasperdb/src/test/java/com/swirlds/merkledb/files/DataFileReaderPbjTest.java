/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.MockitoAnnotations.openMocks;

import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.merkledb.config.MerkleDbConfig;
import com.swirlds.merkledb.test.fixtures.ExampleFixedSizeDataSerializer;
import java.io.File;
import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class DataFileReaderPbjTest {

    private final MerkleDbConfig dbConfig = ConfigurationHolder.getConfigData(MerkleDbConfig.class);

    @Mock
    private DataFileMetadata dataFileMetadata;

    private File file;
    private DataFileReaderPbj dataFileReaderPbj;

    @BeforeEach
    void setUp() throws IOException {
        openMocks(this);
        file = File.createTempFile("file-reader", "test");
        dataFileReaderPbj =
                new DataFileReaderPbj(dbConfig, file.toPath(), new ExampleFixedSizeDataSerializer(), dataFileMetadata);
    }

    /**
     * Current algorithm for leasing file channels is to use a round-robin-like approach with one peculiarity to keep in mind:
     * it presumes that leases are going to be released shortely after they are taken. In this case, there will be even
     * distribution of leases among all available file channels. However, if a lease is not released, leases will be heavily
     * biased towards the first file channel, as this test demonstrates.
     */
    @Test
    void testLeaseFileChannel() throws IOException {
        for (int i = 0; i < dataFileReaderPbj.getThreadsPerFileChannel(); i++) {
            int lease = dataFileReaderPbj.leaseFileChannel();
            assertEquals(0, lease);
        }

        // opening new channel
        assertEquals(1, dataFileReaderPbj.leaseFileChannel());

        assertEquals(0, dataFileReaderPbj.leaseFileChannel());
        assertEquals(1, dataFileReaderPbj.leaseFileChannel());
        assertEquals(0, dataFileReaderPbj.leaseFileChannel());
        for (int i = 0; i < 5; i++) {
            dataFileReaderPbj.leaseFileChannel();
        }

        assertEquals(0, dataFileReaderPbj.leaseFileChannel());
        assertEquals(1, dataFileReaderPbj.leaseFileChannel());
        assertEquals(2, dataFileReaderPbj.leaseFileChannel());
        assertEquals(0, dataFileReaderPbj.leaseFileChannel());
        assertEquals(1, dataFileReaderPbj.leaseFileChannel());
        assertEquals(2, dataFileReaderPbj.leaseFileChannel());
    }

    @Test
    void testLeaseReleaseFileChannel() throws IOException {
        for (int i = 0; i < dataFileReaderPbj.getThreadsPerFileChannel(); i++) {
            int lease = dataFileReaderPbj.leaseFileChannel();
            assertEquals(0, lease);
        }

        // opening new channel
        assertEquals(1, dataFileReaderPbj.leaseFileChannel());

        dataFileReaderPbj.releaseFileChannel();
        dataFileReaderPbj.releaseFileChannel();
        dataFileReaderPbj.releaseFileChannel();

        assertEquals(1, dataFileReaderPbj.leaseFileChannel());
        assertEquals(0, dataFileReaderPbj.leaseFileChannel());
        assertEquals(1, dataFileReaderPbj.leaseFileChannel());
    }

    @Test
    void testLeaseFileChannel_maxFileChannels() throws IOException {
        for (int i = 0;
                i < dataFileReaderPbj.getThreadsPerFileChannel() * dataFileReaderPbj.getMaxFileChannels();
                i++) {
            dataFileReaderPbj.leaseFileChannel();
        }

        // verifying that all channels were created
        assertEquals(
                dataFileReaderPbj.getMaxFileChannels(),
                dataFileReaderPbj.fileChannelsCount.get(),
                "File channel count is unexpected");

        assertEquals(1, dataFileReaderPbj.leaseFileChannel());
        assertEquals(2, dataFileReaderPbj.leaseFileChannel());

        // verifying that no additional channels were created
        assertEquals(
                dataFileReaderPbj.getMaxFileChannels(),
                dataFileReaderPbj.fileChannelsCount.get(),
                "File channel count is unexpected");
    }

    @Test
    void testLeaseFileChannel_leaseLeastUsed() throws IOException {
        for (int i = 0;
                i < dataFileReaderPbj.getThreadsPerFileChannel() * dataFileReaderPbj.getMaxFileChannels();
                i++) {
            dataFileReaderPbj.leaseFileChannel();
        }

        dataFileReaderPbj.releaseFileChannel();

        assertEquals(0, dataFileReaderPbj.leaseFileChannel());
        assertEquals(1, dataFileReaderPbj.leaseFileChannel());
        assertEquals(2, dataFileReaderPbj.leaseFileChannel());
    }

    @AfterEach
    public void tearDown() {
        file.deleteOnExit();
    }
}
