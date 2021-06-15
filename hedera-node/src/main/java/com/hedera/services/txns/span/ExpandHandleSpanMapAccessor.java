package com.hedera.services.txns.span;

import com.hedera.services.grpc.marshalling.ImpliedTransfers;
import com.hedera.services.utils.TxnAccessor;

public class ExpandHandleSpanMapAccessor {
	static final String VALIDATED_TRANSFERS_KEY = "validatedTransfers";

	public void setImpliedTransfers(TxnAccessor accessor, ImpliedTransfers impliedTransfers) {
		accessor.getSpanMap().put(VALIDATED_TRANSFERS_KEY, impliedTransfers);
	}

	public ImpliedTransfers getImpliedTransfers(TxnAccessor accessor) {
		return (ImpliedTransfers) accessor.getSpanMap().get(VALIDATED_TRANSFERS_KEY);
	}
}
