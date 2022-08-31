/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.files;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.FileID;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.BDDMockito;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HybridResouceLoaderTest {
    private static final String EXTANT_RESOURCE = "expiry-throttle.json";
    private static final String MISSING_RESOURCE = "renew-throttle.json";
    private static final String SOME_FILE_ID_LITERAL = "0.0.666";
    private static final FileID SOME_FILE_ID = IdUtils.asFile(SOME_FILE_ID_LITERAL);

    @Mock private TieredHederaFs hfs;

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
