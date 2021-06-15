package com.hedera.services.grpc.marshalling;

import com.google.common.base.MoreObjects;
import com.hedera.services.ledger.BalanceChange;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.Collections;
import java.util.List;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class ImpliedTransfers {
	private final ImpliedTransfersMeta meta;
	private final List<BalanceChange> changes;

	private ImpliedTransfers(ImpliedTransfersMeta meta, List<BalanceChange> changes) {
		this.meta = meta;
		this.changes = changes;
	}

	public static ImpliedTransfers valid(
			int maxHbarAdjusts,
			int maxTokenAdjusts,
			List<BalanceChange> changes
	) {
		final var meta = new ImpliedTransfersMeta(maxHbarAdjusts, maxTokenAdjusts, OK);
		return new ImpliedTransfers(meta, changes);
	}

	public static ImpliedTransfers invalid(
			int maxHbarAdjusts,
			int maxTokenAdjusts,
			ResponseCodeEnum code
	) {
		final var meta = new ImpliedTransfersMeta(maxHbarAdjusts, maxTokenAdjusts, code);
		return new ImpliedTransfers(meta, Collections.emptyList());
	}

	public ImpliedTransfersMeta getMeta() {
		return meta;
	}

	public List<BalanceChange> getChanges() {
		return changes;
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
		return MoreObjects.toStringHelper(ImpliedTransfers.class)
				.add("meta", meta)
				.add("changes", changes)
				.toString();
	}
}
