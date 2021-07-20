package com.hedera.services.grpc.marshalling;

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

import com.hedera.services.state.submerkle.FcCustomFee;
import com.hedera.services.store.models.Id;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class CustomFeeMeta {
	public static final CustomFeeMeta MISSING_META =
			new CustomFeeMeta(Id.MISSING_ID, Id.MISSING_ID, Collections.emptyList());

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
