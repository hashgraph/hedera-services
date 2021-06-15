package com.hedera.services.ledger;

import com.google.common.base.MoreObjects;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.hedera.services.ledger.BalanceChange.hbarAdjust;
import static com.hedera.services.ledger.BalanceChange.tokenAdjust;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;

public class ImpliedTransfers {
	private final GlobalDynamicProperties dynamicProperties;
	private final PureTransferSemanticChecks transferSemanticChecks;

	public ImpliedTransfers(
			GlobalDynamicProperties dynamicProperties,
			PureTransferSemanticChecks transferSemanticChecks
	) {
		this.dynamicProperties = dynamicProperties;
		this.transferSemanticChecks = transferSemanticChecks;
	}

	public Pair<List<BalanceChange>, Meta> parseFromGrpc(CryptoTransferTransactionBody op) {
		final var maxTokenAdjusts = dynamicProperties.maxTokenTransferListSize();
		final var maxHbarAdjusts = dynamicProperties.maxTransferListSize();

		final var numHbarAdjusts = op.getTransfers().getAccountAmountsCount();
		if (numHbarAdjusts > maxHbarAdjusts) {
			return invalidChanges(maxHbarAdjusts, maxTokenAdjusts, TRANSFER_LIST_SIZE_LIMIT_EXCEEDED);
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
		return Pair.of(changes, new Meta(maxHbarAdjusts, maxTokenAdjusts, OK));
	}

	private Pair<List<BalanceChange>, Meta> invalidChanges(
			int maxHbarAdjusts,
			int maxTokenAdjusts,
			ResponseCodeEnum code
	) {
		return Pair.of(Collections.emptyList(), new Meta(maxHbarAdjusts, maxTokenAdjusts, code));
	}

	public static class Meta {
		private final long maxExplicitHbarAdjusts;
		private final long maxExplicitTokenAdjusts;
		private final ResponseCodeEnum code;

		public Meta(
				long maxExplicitHbarAdjusts,
				long maxExplicitTokenAdjusts,
				ResponseCodeEnum code
		) {
			this.code = code;
			this.maxExplicitHbarAdjusts = maxExplicitHbarAdjusts;
			this.maxExplicitTokenAdjusts = maxExplicitTokenAdjusts;
		}

		public long getMaxExplicitHbarAdjusts() {
			return maxExplicitHbarAdjusts;
		}

		public long getMaxExplicitTokenAdjusts() {
			return maxExplicitTokenAdjusts;
		}

		public ResponseCodeEnum code() {
			return code;
		}

		/* NOTE: The object methods below are only overridden to improve
				readability of unit tests; this model object is not used in hash-based
				collections, so the performance of these methods doesn't matter. */
		@Override
		public boolean equals(Object obj) {
			return EqualsBuilder.reflectionEquals(this, obj);
		}

		@Override
		public int hashCode() {
			return HashCodeBuilder.reflectionHashCode(this);
		}

		@Override
		public String toString() {
			return MoreObjects.toStringHelper(Meta.class)
					.add("code", code)
					.add("maxExplicitHbarAdjusts", maxExplicitHbarAdjusts)
					.add("maxExplicitTokenAdjusts", maxExplicitTokenAdjusts)
					.toString();
		}
	}
}
