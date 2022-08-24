package com.hedera.services.files;

import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.FileID;
import io.grpc.testing.TestUtils;
import org.apache.logging.log4j.core.config.yaml.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class HybridResouceLoaderTest {
    private static final String EXTANT_RESOURCE = "expiry-throttle.json";
    private static final String MISSING_RESOURCE = "renew-throttle.json";
    private static final String SOME_FILE_ID_LITERAL = "0.0.666";
    private static final FileID SOME_FILE_ID = IdUtils.asFile(SOME_FILE_ID_LITERAL);

    @Mock
    private TieredHederaFs hfs;

    private HybridResouceLoader subject;

    @BeforeEach
    void setUp() {
        subject = new HybridResouceLoader(hfs);
      }

    @Test
    void successfullyLoadsPackagedResourceIfPresent() {
        final var packagedBytes = subject.readAllBytesIfPresent(EXTANT_RESOURCE);
        assertNotNull(packagedBytes);
        assertTrue(packagedBytes.length > 0);
      }

    @Test
    void returnsNullOnMissingPackagedResource() {
        final var packagedBytes = subject.readAllBytesIfPresent(MISSING_RESOURCE);
        assertNull(packagedBytes);
    }

    @Test
    void returnsHfsResourceIfPresent() {
        final var fileBytes = "SOMETHING ELSE".getBytes(StandardCharsets.UTF_8);
        BDDMockito.given(hfs.cat(SOME_FILE_ID)).willReturn(fileBytes);
        final var actual = subject.readAllBytesIfPresent(SOME_FILE_ID_LITERAL);
        assertSame(fileBytes, actual);
      }

    @Test
    void returnsNullIfHfsResourceMissing() {
        BDDMockito.given(hfs.cat(SOME_FILE_ID)).willThrow(IllegalArgumentException.class);
        final var actual = subject.readAllBytesIfPresent(SOME_FILE_ID_LITERAL);
        assertNull(actual);
    }
}