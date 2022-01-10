package com.hedera.services.sigs.metadata;

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

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcCustomFee;

import java.util.Optional;

/**
 * Represents metadata about the signing attributes of a Hedera token.
 */
public class TokenSigningMetadata {
	private final Optional<JKey> adminKey;
	private final Optional<JKey> kycKey;
	private final Optional<JKey> wipeKey;
	private final Optional<JKey> freezeKey;
	private final Optional<JKey> supplyKey;
	private final Optional<JKey> feeScheduleKey;
	private final Optional<JKey> pauseKey;
	private final boolean hasRoyaltyWithFallback;
	private final EntityId treasury;

	private TokenSigningMetadata(
			Optional<JKey> adminKey,
			Optional<JKey> kycKey,
			Optional<JKey> wipeKey,
			Optional<JKey> freezeKey,
			Optional<JKey> supplyKey,
			Optional<JKey> feeScheduleKey,
			Optional<JKey> pauseKey,
			boolean hasRoyaltyWithFallback,
			EntityId treasury
	) {
		this.adminKey = adminKey;
		this.kycKey = kycKey;
		this.wipeKey = wipeKey;
		this.freezeKey = freezeKey;
		this.supplyKey = supplyKey;
		this.treasury = treasury;
		this.feeScheduleKey = feeScheduleKey;
		this.pauseKey = pauseKey;
		this.hasRoyaltyWithFallback = hasRoyaltyWithFallback;
	}

	public static TokenSigningMetadata from(MerkleToken token) {
		boolean hasRoyaltyWithFallback = false;
		final var customFees = token.customFeeSchedule();
		if (!customFees.isEmpty()) {
			for (var fee : customFees) {
				if (fee.getFeeType() != FcCustomFee.FeeType.ROYALTY_FEE) {
					continue;
				}
				if (fee.getRoyaltyFeeSpec().fallbackFee() != null) {
					hasRoyaltyWithFallback = true;
					break;
				}
			}
		}
		return new TokenSigningMetadata(
				token.adminKey(),
				token.kycKey(),
				token.wipeKey(),
				token.freezeKey(),
				token.supplyKey(),
				token.feeScheduleKey(),
				token.pauseKey(),
				hasRoyaltyWithFallback,
				token.treasury());
	}

	public Optional<JKey> adminKey() {
		return adminKey;
	}

	public Optional<JKey> optionalFreezeKey() {
		return freezeKey;
	}

	public Optional<JKey> optionalKycKey() {
		return kycKey;
	}

	public Optional<JKey> optionalWipeKey() {
		return wipeKey;
	}

	public Optional<JKey> optionalSupplyKey() {
		return supplyKey;
	}

	public Optional<JKey> optionalFeeScheduleKey() {
		return feeScheduleKey;
	}

	public Optional<JKey> optionalPauseKey() {
		return pauseKey;
	}

	public boolean hasRoyaltyWithFallback() {
		return hasRoyaltyWithFallback;
	}

	public EntityId treasury() {
		return treasury;
	}
}
