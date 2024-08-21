/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.cli.test.sign;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doCallRealMethod;

import com.hedera.services.cli.sign.RecordStreamType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecordStreamTypeTest {

    @Mock
    private RecordStreamType subject;

    @Test
    @DisplayName("Record stream type test")
    void testRecordStreamMetadata() {
        String gzFile = "test.rcd.gz";
        doCallRealMethod().when(subject).getDescription();
        doCallRealMethod().when(subject).getExtension();
        doCallRealMethod().when(subject).getSigExtension();
        doCallRealMethod().when(subject).getFileHeader();
        doCallRealMethod().when(subject).getSigFileHeader();
        doCallRealMethod().when(subject).isGzFile(gzFile);

        String expectedRecordDescription = "records";
        assertEquals(expectedRecordDescription, subject.getDescription());
        String expectedRecordExtension = "rcd";
        assertEquals(expectedRecordExtension, subject.getExtension());
        String expectedRecordSigExtension = "rcd_sig";
        assertEquals(expectedRecordSigExtension, subject.getSigExtension());
        int[] fileHeader = {6};
        assertArrayEquals(fileHeader, subject.getFileHeader());
        byte[] signFileHeader = {6};
        assertArrayEquals(signFileHeader, subject.getSigFileHeader());
        assertTrue(subject.isGzFile(gzFile));
    }
}
