package com.hedera.node.app.service.token.records;

import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

public interface TokenBurnRecordBuilder extends SingleTransactionRecordBuilder {

    @NonNull
    TokenMintRecordBuilder newTotalSupply(final long newTotalSupply);

    @NonNull
    TokenMintRecordBuilder serialNumbers(@NonNull List<Long> serialNumbers);

}
