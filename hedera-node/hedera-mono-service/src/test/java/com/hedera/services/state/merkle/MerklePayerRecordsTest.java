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
package com.hedera.services.state.merkle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.services.evm.store.contracts.utils.BytesKey;
import com.hedera.services.state.migration.QueryableRecords;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.utils.SeededPropertySource;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MerklePayerRecordsTest {
    @Test
    void alwaysHasMutableFcq() {
        final var subject = new MerklePayerRecords();
        assertNotNull(subject.mutableQueue());
    }

    @Test
    void queryableRecordsCanChange() {
        final var subject = new MerklePayerRecords();
        assertSame(QueryableRecords.NO_QUERYABLE_RECORDS, subject.asQueryableRecords());

        final var someRecord = SeededPropertySource.forSerdeTest(11, 1).nextRecord();
        subject.offer(someRecord);
        final var queryable = subject.asQueryableRecords();
        assertEquals(1, queryable.expectedSize());
    }

    @Test
    void isSelfHashing() {
        final var subject = new MerklePayerRecords();

        assertTrue(subject.isSelfHashing());
    }

    @Test
    void hashDependsOnNumberAndRecords() {
        final var subject = new MerklePayerRecords();
        final var emptyHash = subject.getHash();

        final var aNum = EntityNum.fromInt(123);
        subject.setKey(aNum);
        final var justNumHash = subject.getHash();

        final var aRecord = SeededPropertySource.forSerdeTest(11, 1).nextRecord();
        subject.offer(aRecord);
        final var oneRecordHash = subject.getHash();
        final var bRecord = SeededPropertySource.forSerdeTest(11, 2).nextRecord();
        subject.offer(bRecord);
        final var twoRecordHash = subject.getHash();

        subject.mutableQueue().poll();
        subject.mutableQueue().poll();
        subject.offer(aRecord);
        final var secondOneRecordHash = subject.getHash();
        assertEquals(oneRecordHash, secondOneRecordHash);

        final Set<BytesKey> uniqueHashes = new HashSet<>();
        uniqueHashes.add(new BytesKey(emptyHash.getValue()));
        uniqueHashes.add(new BytesKey(justNumHash.getValue()));
        uniqueHashes.add(new BytesKey(oneRecordHash.getValue()));
        uniqueHashes.add(new BytesKey(twoRecordHash.getValue()));
        assertEquals(4, uniqueHashes.size());
    }
}
