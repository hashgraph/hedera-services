package com.hedera.services.state.migration;

import com.hedera.services.state.submerkle.ExpirableTxnRecord;

import java.util.Collections;
import java.util.Iterator;

public record QueryableRecords(int expectedSize, Iterator<ExpirableTxnRecord> iterator) {
    public static final QueryableRecords NO_QUERYABLE_RECORDS = new QueryableRecords(
            0, Collections.emptyIterator());
}
