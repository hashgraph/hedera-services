package com.hedera.evm.txns.span;

import com.hedera.evm.usage.token.meta.FeeScheduleUpdateMeta;
import com.hedera.evm.usage.token.meta.TokenWipeMeta;
import com.hedera.evm.utils.accessors.SignedTxnAccessor;
import com.hedera.evm.utils.accessors.TxnAccessor;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ExpandHandleSpanMapAccessor {
    private static final String TOKEN_WIPE_META_KEY = "tokenWipeMeta";
    private static final String FEE_SCHEDULE_UPDATE_META_KEY = "feeScheduleUpdateMeta";


    @Inject
    public ExpandHandleSpanMapAccessor() {
        // Default constructor
    }

    public void setTokenWipeMeta(TxnAccessor accessor, TokenWipeMeta tokenWipeMeta) {
        accessor.getSpanMap().put(TOKEN_WIPE_META_KEY, tokenWipeMeta);
    }

    public void setFeeScheduleUpdateMeta(
            TxnAccessor accessor, FeeScheduleUpdateMeta feeScheduleUpdateMeta) {
        accessor.getSpanMap().put(FEE_SCHEDULE_UPDATE_META_KEY, feeScheduleUpdateMeta);
    }
}
