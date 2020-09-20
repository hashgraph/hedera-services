package com.hedera.services.state.submerkle;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.google.common.base.MoreObjects;
import com.hedera.services.state.merkle.MerkleAccountState;
import com.hedera.services.state.merkle.MerkleToken;
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenKycStatus;
import com.hederahashgraph.api.proto.java.TokenRelationship;

import java.util.Arrays;
import java.util.Objects;

import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.FreezeNotApplicable;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.Frozen;
import static com.hederahashgraph.api.proto.java.TokenFreezeStatus.Unfrozen;
import static com.hederahashgraph.api.proto.java.TokenKycStatus.Granted;
import static com.hederahashgraph.api.proto.java.TokenKycStatus.KycNotApplicable;
import static com.hederahashgraph.api.proto.java.TokenKycStatus.Revoked;

public class RawTokenRelationship {
	private final long balance;
	private final long shardNum;
	private final long realmNum;
	private final long tokenNum;
	private final boolean frozen;
	private final boolean kycGranted;

	public RawTokenRelationship(
			long balance,
			long shardNum,
			long realmNum,
			long tokenNum,
			boolean frozen,
			boolean kycGranted
	) {
		this.balance = balance;
		this.shardNum = shardNum;
		this.realmNum = realmNum;
		this.tokenNum = tokenNum;
		this.frozen = frozen;
		this.kycGranted = kycGranted;
	}

	public long getBalance() {
		return balance;
	}

	public boolean isFrozen() {
		return frozen;
	}

	public boolean isKycGranted() {
		return kycGranted;
	}

	public long getTokenNum() {
		return tokenNum;
	}

	public TokenID id() {
		return TokenID.newBuilder()
				.setTokenNum(tokenNum)
				.build();
	}

	public TokenRelationship asGrpcFor(MerkleToken token) {
		return TokenRelationship.newBuilder()
				.setBalance(balance)
				.setSymbol(token.symbol())
				.setTokenId(TokenID.newBuilder()
						.setShardNum(shardNum)
						.setRealmNum(realmNum)
						.setTokenNum(tokenNum))
				.setFreezeStatus(freezeStatusFor(token))
				.setKycStatus(kycStatusFor(token))
				.build();

	}

	private TokenFreezeStatus freezeStatusFor(MerkleToken token) {
		return token.hasFreezeKey()
				? (frozen ? Frozen : Unfrozen)
				: FreezeNotApplicable;
	}

	private TokenKycStatus kycStatusFor(MerkleToken token) {
		return token.hasKycKey()
				? (kycGranted ? Granted : Revoked)
				: KycNotApplicable;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || RawTokenRelationship.class != o.getClass()) {
			return false;
		}

		var that = (RawTokenRelationship) o;

		return this.balance == that.balance && this.frozen == that.frozen && this.kycGranted == that.kycGranted;
	}

	@Override
	public int hashCode() {
		return Objects.hash(balance, frozen, kycGranted);
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("token", String.format("%d.%d.%d", shardNum, realmNum, tokenNum))
				.add("balance", balance)
				.add("frozen", frozen)
				.add("kycGranted", kycGranted)
				.toString();
	}
}

