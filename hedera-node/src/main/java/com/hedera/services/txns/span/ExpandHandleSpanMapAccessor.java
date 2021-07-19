package com.hedera.services.txns.span;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.grpc.marshalling.ImpliedTransfers;
import com.hedera.services.usage.token.meta.FeeScheduleUpdateMeta;
import com.hedera.services.utils.TxnAccessor;

/**
 * Minimal helper class for getting/setting entries in a span map.
 */
public class ExpandHandleSpanMapAccessor {
	private static final String IMPLIED_TRANSFERS_KEY = "impliedTransfers";
	private static final String FEE_SCHEDULE_UPDATE_META_KEY = "feeScheduleUpdateMeta";

	public void setFeeScheduleUpdateMeta(TxnAccessor accessor, FeeScheduleUpdateMeta feeScheduleUpdateMeta) {
		accessor.getSpanMap().put(FEE_SCHEDULE_UPDATE_META_KEY, feeScheduleUpdateMeta);
	}

	public FeeScheduleUpdateMeta getFeeScheduleUpdateMeta(TxnAccessor accessor) {
		return (FeeScheduleUpdateMeta) accessor.getSpanMap().get(FEE_SCHEDULE_UPDATE_META_KEY);
	}

	public void setImpliedTransfers(TxnAccessor accessor, ImpliedTransfers impliedTransfers) {
		accessor.getSpanMap().put(IMPLIED_TRANSFERS_KEY, impliedTransfers);
	}

	public ImpliedTransfers getImpliedTransfers(TxnAccessor accessor) {
		return (ImpliedTransfers) accessor.getSpanMap().get(IMPLIED_TRANSFERS_KEY);
	}
}
