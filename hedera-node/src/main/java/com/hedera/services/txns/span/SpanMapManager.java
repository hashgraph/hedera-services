package com.hedera.services.txns.span;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.utils.TxnAccessor;

import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;

public class SpanMapManager {
	private final GlobalDynamicProperties dynamicProperties;
	private final ImpliedTransfersMarshal impliedTransfersMarshal;
	private final ExpandHandleSpanMapAccessor spanMapAccessor = new ExpandHandleSpanMapAccessor();

	public SpanMapManager(
			ImpliedTransfersMarshal impliedTransfersMarshal,
			GlobalDynamicProperties dynamicProperties
	) {
		this.impliedTransfersMarshal = impliedTransfersMarshal;
		this.dynamicProperties = dynamicProperties;
	}

	public void expandSpan(TxnAccessor accessor) {
		if (accessor.getFunction() == CryptoTransfer) {
			expandImpliedTransfers(accessor);
		}
	}

	public void rationalizeSpan(TxnAccessor accessor) {
		if (accessor.getFunction() == CryptoTransfer) {
			rationalizeImpliedTransfers(accessor);
		}
	}

	private void rationalizeImpliedTransfers(TxnAccessor accessor) {
		final var impliedTransfers = spanMapAccessor.getImpliedTransfers(accessor);
		if (impliedTransfers.getMeta().wasDerivedFrom(dynamicProperties)) {
			return;
		} else {
			expandImpliedTransfers(accessor);
		}
	}

	private void expandImpliedTransfers(TxnAccessor accessor) {
		final var op = accessor.getTxn().getCryptoTransfer();
		final var impliedTransfers = impliedTransfersMarshal.marshalFromGrpc(op);

		spanMapAccessor.setImpliedTransfers(accessor, impliedTransfers);
	}
}
