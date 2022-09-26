/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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

import static com.hedera.services.files.MetadataMapFactory.metaMapFrom;
import static com.hedera.services.files.MetadataMapFactory.toAttr;
import static com.hedera.services.files.MetadataMapFactory.toFid;
import static com.hedera.services.files.MetadataMapFactory.toKeyString;
import static com.hedera.services.files.MetadataMapFactory.toValueBytes;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;

import com.hedera.services.fees.calculation.FeeCalcUtilsTest;
import com.hedera.services.files.store.FcBlobsBytesStore;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hedera.test.utils.IdUtils;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MetadataMapFactoryTest {
    private static final long expiry = 1_234_567L;

    @Test
    void toAttrMirrorsNulls() {
        assertNull(toAttr(null));
    }

    @Test
    void toAttrThrowsIaeOnError() {
        final var bytes = "NONSENSE".getBytes();
        assertThrows(IllegalArgumentException.class, () -> toAttr(bytes));
    }

    @Test
    void toAttrTreatsTombstoneAsMissing() {
        assertNull(toAttr(FcBlobsBytesStore.EMPTY_BLOB.getData()));
    }

    @Test
    void toValueThrowsIaeOnError() throws IOException {
        final var suspect = mock(HFileMeta.class);
        given(suspect.serialize()).willThrow(IOException.class);

        assertThrows(IllegalArgumentException.class, () -> toValueBytes(suspect));
    }

    @Test
    void toValueConversionWorks() throws Exception {
        final var validKey = TxnHandlingScenario.MISC_FILE_WACL_KT.asJKey();
        final var attr = new HFileMeta(false, validKey, expiry);
        final var expected = attr.serialize();

        final var actual = toValueBytes(attr);

        assertArrayEquals(expected, actual);
    }

    @Test
    void toAttrConversionWorks() throws Exception {
        final var validKey = TxnHandlingScenario.MISC_FILE_WACL_KT.asJKey();
        final var expected = new HFileMeta(false, validKey, expiry);
        final var bytes = expected.serialize();

        final var actual = toAttr(bytes);

        assertEquals(expected.toString(), actual.toString());
    }

    @Test
    void toFidConversionWorks() {
        final var key = "/666/k888";
        final var expected = IdUtils.asFile("0.666.888");

        final var actual = toFid(key);

        assertEquals(expected, actual);
    }

    @Test
    void toKeyConversionWorks() {
        final var fid = IdUtils.asFile("0.2.3");
        final var expected = FeeCalcUtilsTest.pathOfMeta(fid);

        final var actual = toKeyString(fid);

        assertEquals(expected, actual);
    }

    @Test
    void productHasMapSemantics() throws Exception {
        final Map<String, byte[]> delegate = new HashMap<>();
        final var wacl = TxnHandlingScenario.MISC_FILE_WACL_KT.asJKey();
        final var attr0 = new HFileMeta(true, wacl, 1_234_567L);
        final var attr1 = new HFileMeta(true, wacl, 7_654_321L);
        final var attr2 = new HFileMeta(false, wacl, 7_654_321L);
        final var attr3 = new HFileMeta(false, wacl, 1_234_567L);
        delegate.put(asLegacyPath("0.2.7"), attr0.serialize());
        final var fid1 = IdUtils.asFile("0.2.3");
        final var fid2 = IdUtils.asFile("0.3333.4");
        final var fid3 = IdUtils.asFile("0.4.555555");
        final var metaMap = metaMapFrom(delegate);

        metaMap.put(fid1, attr1);
        metaMap.put(fid2, attr2);
        metaMap.put(fid3, attr3);

        assertFalse(metaMap.isEmpty());
        assertEquals(4, metaMap.size());
        metaMap.remove(fid2);
        assertEquals(3, metaMap.size());
        assertEquals(
                String.format(
                        "/2/k3->%s, /4/k555555->%s, /2/k7->%s",
                        Arrays.toString(attr1.serialize()),
                        Arrays.toString(attr3.serialize()),
                        Arrays.toString(attr0.serialize())),
                delegate.entrySet().stream()
                        .sorted(
                                Comparator.comparingLong(
                                        entry ->
                                                Long.parseLong(
                                                        entry.getKey()
                                                                .substring(
                                                                        entry.getKey().indexOf('k')
                                                                                + 1,
                                                                        entry.getKey().indexOf('k')
                                                                                + 2))))
                        .map(
                                entry ->
                                        String.format(
                                                "%s->%s",
                                                entry.getKey(), Arrays.toString(entry.getValue())))
                        .collect(Collectors.joining(", ")));

        assertTrue(metaMap.containsKey(fid1));
        assertFalse(metaMap.containsKey(fid2));

        metaMap.clear();
        assertTrue(metaMap.isEmpty());
    }

    private String asLegacyPath(final String fid) {
        return FeeCalcUtilsTest.pathOfMeta(IdUtils.asFile(fid));
    }
}
