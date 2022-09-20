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
package com.hedera.evm.txns.span;

import com.hedera.evm.usage.token.meta.FeeScheduleUpdateMeta;
import com.hedera.evm.usage.token.meta.TokenWipeMeta;
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
