package com.hedera.services.grpc.marshalling;

import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.store.models.Id;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.List;
import java.util.Objects;

public class CustomFeeMeta {
	private final Id tokenId;
	private final Id treasuryId;
	private final List<FcCustomFee> customFees;

	public CustomFeeMeta(Id tokenId, Id treasuryId, List<FcCustomFee> customFees) {
		this.tokenId = tokenId;
		this.treasuryId = treasuryId;
		this.customFees = customFees;
	}

	public Id getTokenId() {
		return tokenId;
	}

	public Id getTreasuryId() {
		return treasuryId;
	}

	public List<FcCustomFee> getCustomFees() {
		return customFees;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || CustomFeeMeta.class != o.getClass()) {
			return false;
		}

		var that = (CustomFeeMeta) o;

		return Objects.equals(this.tokenId, that.tokenId)
				&& Objects.equals(this.treasuryId, that.treasuryId)
				&& Objects.equals(this.customFees, that.customFees);
	}

	@Override
	public String toString() {
		return "CustomFeeMeta{" +
				"tokenId=" + tokenId +
				", treasuryId=" + treasuryId +
				", customFees=" + customFees +
				'}';
	}

	@Override
	public int hashCode() {
		return HashCodeBuilder.reflectionHashCode(this);
	}
}
