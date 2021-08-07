package com.hedera.services.state.merkle.v3.files;

import com.swirlds.common.crypto.Hash;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.hedera.services.state.merkle.v3.V3TestUtils.deleteDirectoryAndContents;
import static com.hedera.services.state.merkle.v3.V3TestUtils.hash;
import static com.hedera.services.state.merkle.v3.files.DataFileCommon.FOOTER_SIZE;
import static com.hedera.services.state.merkle.v3.files.DataFileCommon.KEY_SIZE;
import static org.junit.jupiter.api.Assertions.*;

/**
 * DataFiles support both fixed size and variable size data, this unit test checks it with fixed size data
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DataFileFixedSizeDataWithDataFileReaderSynchronousTest extends DataFileFixedSizeDataWithDataFileReaderThreadLocalTest {

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