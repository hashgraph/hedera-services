package com.hedera.services.store.tokens.views.internals;

import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.commons.lang3.tuple.Pair;

public class PermHashLong {
	private final long value;

	public PermHashLong(long value) {
		this.value = value;
	}

	public static PermHashLong fromModelRel(TokenRelationship tokenRelationship) {
		throw new AssertionError("Not implemented!");
	}

	public static PermHashLong asPhl(long i) {
		return new PermHashLong(i);
	}

	public static PermHashLong asPhl(int hi, int lo) {
		throw new AssertionError("Not implemented!");
	}

	public static PermHashLong asPhl(long hi, long lo) {
		throw new AssertionError("Not implemented!");
	}

	public static PermHashLong fromNftId(NftId id) {
		throw new AssertionError("Not implemented!");
	}

	public TokenID hiAsGrpcTokenId() {
		throw new AssertionError("Not implemented!");
	}

	public PermHashInteger hiAsPhi() {
		throw new AssertionError("Not implemented!");
	}

	public Pair<AccountID, TokenID> asAccountTokenRel() {
		return Pair.of(
				AccountID.newBuilder()
						.setAccountNum(-1)
						.build(),
				TokenID.newBuilder()
						.setTokenNum(-1)
						.build());
	}
	@Override
	public int hashCode() {
		return (int) MiscUtils.perm64(value);
	}

	public long getValue() {
		return value;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || PermHashLong.class != o.getClass()) {
			return false;
		}

		var that = (PermHashLong) o;

		return this.value == that.value;
	}
}
