package com.swirlds.merkledb.files;

import static com.swirlds.merkledb.files.DataFileReaderPbj.MAX_FILE_CHANNELS;
import static com.swirlds.merkledb.files.DataFileReaderPbj.THREADS_PER_FILECHANNEL;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.MockitoAnnotations.openMocks;

import com.swirlds.merkledb.test.fixtures.ExampleFixedSizeDataSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

class DataFileReaderPbjTest {

    @Mock
    private DataFileMetadata dataFileMetadata;
    private File file;
    private DataFileReaderPbj dataFileReaderPbj;

    @BeforeEach
    void setUp() throws IOException {
        openMocks(this);
        file = File.createTempFile( "file-reader", "test");
        dataFileReaderPbj = new DataFileReaderPbj(file.toPath(), new ExampleFixedSizeDataSerializer(), dataFileMetadata);
    }

    @Test
    void testLeaseFileChannel() throws IOException {
        for (int i = 0; i < THREADS_PER_FILECHANNEL; i++) {
            int lease = dataFileReaderPbj.leaseFileChannel();
            assertEquals(0, lease);
        }

        assertEquals(1, dataFileReaderPbj.leaseFileChannel());

        for (int i = 0; i < THREADS_PER_FILECHANNEL - 1; i++) {
            int lease = dataFileReaderPbj.leaseFileChannel();
            assertEquals(1, lease);
        }

        assertEquals(2, dataFileReaderPbj.leaseFileChannel());
    }

    @Test
    void testLeaseFileChannel_maxFileChannels() throws IOException {
        for (int i = 0; i < THREADS_PER_FILECHANNEL * MAX_FILE_CHANNELS; i++) {
            dataFileReaderPbj.leaseFileChannel();
        }

        // verifying that all channels were created
        assertEquals(MAX_FILE_CHANNELS, dataFileReaderPbj.fileChannelsCount.get(), "File channel count is unexpected");

        // verifying even distribution
        for (int i = 0; i < MAX_FILE_CHANNELS; i++) {
            assertEquals(8, dataFileReaderPbj.fileChannelsLeases.get(i));
        }

        assertEquals(0, dataFileReaderPbj.leaseFileChannel());
        assertEquals(1, dataFileReaderPbj.leaseFileChannel());

        // verifying that no additional channels were created
        assertEquals(MAX_FILE_CHANNELS, dataFileReaderPbj.fileChannelsCount.get(), "File channel count is unexpected");
    }

    @Test
    void testLeaseFileChannel_leaseLeastUsed () throws IOException {
        for (int i = 0; i < THREADS_PER_FILECHANNEL * MAX_FILE_CHANNELS; i++) {
            dataFileReaderPbj.leaseFileChannel();
        }

        dataFileReaderPbj.releaseFileChannel(5);
        dataFileReaderPbj.releaseFileChannel(5);

        dataFileReaderPbj.releaseFileChannel(4);

        assertEquals(5, dataFileReaderPbj.leaseFileChannel());
        assertEquals(4, dataFileReaderPbj.leaseFileChannel());
    }

    @AfterEach
    public void tearDown() {
        file.deleteOnExit();
    }
}