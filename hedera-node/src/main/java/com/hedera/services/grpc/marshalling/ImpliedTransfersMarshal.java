package com.hedera.services.grpc.marshalling;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.ledger.PureTransferSemanticChecks;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;

import java.util.ArrayList;
import java.util.List;

import static com.hedera.services.ledger.BalanceChange.hbarAdjust;
import static com.hedera.services.ledger.BalanceChange.tokenAdjust;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;

public class ImpliedTransfersMarshal {
	private final GlobalDynamicProperties dynamicProperties;
	private final PureTransferSemanticChecks transferSemanticChecks;

	public ImpliedTransfersMarshal(
			GlobalDynamicProperties dynamicProperties,
			PureTransferSemanticChecks transferSemanticChecks
	) {
		this.dynamicProperties = dynamicProperties;
		this.transferSemanticChecks = transferSemanticChecks;
	}

	public ImpliedTransfers marshalFromGrpc(CryptoTransferTransactionBody op) {
		final var maxTokenAdjusts = dynamicProperties.maxTokenTransferListSize();
		final var maxHbarAdjusts = dynamicProperties.maxTransferListSize();

		final var numHbarAdjusts = op.getTransfers().getAccountAmountsCount();
		if (numHbarAdjusts > maxHbarAdjusts) {
			return ImpliedTransfers.invalid(maxHbarAdjusts, maxTokenAdjusts, TRANSFER_LIST_SIZE_LIMIT_EXCEEDED);
		}

		final List<BalanceChange> changes = new ArrayList<>();
		for (var aa : op.getTransfers().getAccountAmountsList()) {
			changes.add(hbarAdjust(Id.fromGrpcAccount(aa.getAccountID()), aa.getAmount()));
		}
		for (var scopedTransfers : op.getTokenTransfersList()) {
			final var scopingToken = Id.fromGrpcToken(scopedTransfers.getToken());
			for (var aa : scopedTransfers.getTransfersList()) {
				changes.add(tokenAdjust(scopingToken, Id.fromGrpcAccount(aa.getAccountID()), aa.getAmount()));
			}
		}

		return ImpliedTransfers.valid(maxHbarAdjusts, maxTokenAdjusts, changes);
	}
}
