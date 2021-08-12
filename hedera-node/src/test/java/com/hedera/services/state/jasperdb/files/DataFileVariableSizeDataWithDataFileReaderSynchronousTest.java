package com.hedera.services.state.jasperdb.files;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.nio.file.Path;

/**
 * DataFiles support both fixed size and variable size data, this unit test checks it with fixed size data
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DataFileVariableSizeDataWithDataFileReaderSynchronousTest extends DataFileVariableSizeDataWithDataFileReaderThreadLocalTest {

    public DataFileReaderFactory factoryForReaderToTest() {
        return new DataFileReaderFactory() {
            @Override
            public DataFileReader newDataFileReader(Path path) throws IOException {
                return new DataFileReaderSynchronous(path);
            }

            @Override
            public DataFileReader newDataFileReader(Path path, DataFileMetadata metadata) throws IOException {
                return new DataFileReaderSynchronous(path,metadata);
            }
        };
    }
}