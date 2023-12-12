package com.hedera.node.app.spi.workflows.record;

import edu.umd.cs.findbugs.annotations.Nullable;

public record RecordListCheckPoint(@Nullable SingleTransactionRecordBuilder firstPrecedingRecord,
                                   @Nullable SingleTransactionRecordBuilder lastFollowingRecord) {
}
