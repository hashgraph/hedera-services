package com.hedera.services.state.merkle;

import com.hedera.services.state.migration.QueryableRecords;
import com.hedera.test.utils.SeededPropertySource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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

        final var someRecord = SeededPropertySource
                .forSerdeTest(11, 1)
                .nextRecord();
        subject.offer(someRecord);
        final var queryable = subject.asQueryableRecords();
        assertEquals(1, queryable.expectedSize());
    }
}