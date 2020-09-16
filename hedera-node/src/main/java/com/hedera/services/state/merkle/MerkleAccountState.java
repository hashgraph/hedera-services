package com.hedera.services.state.merkle;

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
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.state.serdes.DomainSerdes;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.RawTokenRelationship;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenBalance;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.common.io.SerializableDataInputStream;
import com.swirlds.common.io.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.utility.AbstractMerkleNode;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.hedera.services.context.properties.StandardizedPropertySources.MAX_MEMO_UTF8_BYTES;
import static com.hedera.services.legacy.core.jproto.JKey.equalUpToDecodability;
import static com.hedera.services.utils.EntityIdUtils.readableId;
import static com.hedera.services.utils.MiscUtils.describe;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_HAS_NO_TOKEN_RELATIONSHIP;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static java.util.stream.Collectors.toList;

public class MerkleAccountState extends AbstractMerkleNode implements MerkleLeaf {
	private static final Logger log = LogManager.getLogger(MerkleAccountState.class);

	static final int MAX_CONCEIVABLE_TOKEN_BALANCES_SIZE = 4_096;
	static final long NOOP_MASK = -1L;
	static final long[] NO_TOKEN_RELATIONSHIPS = new long[0];
	static final int NUM_TOKEN_PROPS = 3;
	static final int BALANCE_OFFSET = 1;
	static final int FLAGS_OFFSET = 2;

	public static long FREEZE_MASK = 1L;
	public static long KYC_MASK = 1L << 1;

	static final int RELEASE_070_VERSION = 1;
	static final int RELEASE_080_VERSION = 2;
	static final int MERKLE_VERSION = RELEASE_080_VERSION;
	static final long RUNTIME_CONSTRUCTABLE_ID = 0x354cfc55834e7f12L;

	static DomainSerdes serdes = new DomainSerdes();

	public static final String DEFAULT_MEMO = "";

	private JKey key;
	private long expiry;
	private long hbarBalance;
	private long autoRenewSecs;
	private long senderThreshold;
	private long receiverThreshold;
	private String memo = DEFAULT_MEMO;
	private boolean deleted;
	private boolean smartContract;
	private boolean receiverSigRequired;
	private EntityId proxy;

	long[] tokenRels = NO_TOKEN_RELATIONSHIPS;

	public MerkleAccountState() {
	}

	public MerkleAccountState(
			JKey key,
			long expiry,
			long hbarBalance,
			long autoRenewSecs,
			long senderThreshold,
			long receiverThreshold,
			String memo,
			boolean deleted,
			boolean smartContract,
			boolean receiverSigRequired,
			EntityId proxy,
			long[] tokenRels
	) {
		if (tokenRels.length % NUM_TOKEN_PROPS != 0) {
			throw new IllegalArgumentException(
					"The token relationships array length must be divisible by " + NUM_TOKEN_PROPS + "!");
		}
		this.key = key;
		this.expiry = expiry;
		this.hbarBalance = hbarBalance;
		this.autoRenewSecs = autoRenewSecs;
		this.senderThreshold = senderThreshold;
		this.receiverThreshold = receiverThreshold;
		this.memo = Optional.ofNullable(memo).orElse(DEFAULT_MEMO);
		this.deleted = deleted;
		this.smartContract = smartContract;
		this.receiverSigRequired = receiverSigRequired;
		this.proxy = proxy;
		this.tokenRels = tokenRels;
	}

	/* --- MerkleLeaf --- */
	@Override
	public long getClassId() {
		return RUNTIME_CONSTRUCTABLE_ID;
	}

	@Override
	public int getVersion() {
		return MERKLE_VERSION;
	}

	@Override
	public void deserialize(SerializableDataInputStream in, int version) throws IOException {
		key = serdes.readNullable(in, serdes::deserializeKey);
		expiry = in.readLong();
		hbarBalance = in.readLong();
		autoRenewSecs = in.readLong();
		senderThreshold = in.readLong();
		receiverThreshold = in.readLong();
		memo = in.readNormalisedString(MAX_MEMO_UTF8_BYTES);
		deleted = in.readBoolean();
		smartContract = in.readBoolean();
		receiverSigRequired = in.readBoolean();
		proxy = serdes.readNullableSerializable(in);
		if (version >= RELEASE_080_VERSION) {
			tokenRels = in.readLongArray(MAX_CONCEIVABLE_TOKEN_BALANCES_SIZE);
		}
	}

	@Override
	public void serialize(SerializableDataOutputStream out) throws IOException {
		serdes.writeNullable(key, out, serdes::serializeKey);
		out.writeLong(expiry);
		out.writeLong(hbarBalance);
		out.writeLong(autoRenewSecs);
		out.writeLong(senderThreshold);
		out.writeLong(receiverThreshold);
		out.writeNormalisedString(memo);
		out.writeBoolean(deleted);
		out.writeBoolean(smartContract);
		out.writeBoolean(receiverSigRequired);
		serdes.writeNullableSerializable(proxy, out);
		out.writeLongArray(tokenRels);
	}

	/* --- Copyable --- */
	public MerkleAccountState copy() {
		return new MerkleAccountState(
				key,
				expiry,
				hbarBalance,
				autoRenewSecs,
				senderThreshold,
				receiverThreshold,
				memo,
				deleted,
				smartContract,
				receiverSigRequired,
				proxy,
				Arrays.copyOf(tokenRels, tokenRels.length));
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || MerkleAccountState.class != o.getClass()) {
			return false;
		}

		var that = (MerkleAccountState) o;

		return this.expiry == that.expiry &&
				this.hbarBalance == that.hbarBalance &&
				this.autoRenewSecs == that.autoRenewSecs &&
				this.senderThreshold == that.senderThreshold &&
				this.receiverThreshold == that.receiverThreshold &&
				Objects.equals(this.memo, that.memo) &&
				this.deleted == that.deleted &&
				this.smartContract == that.smartContract &&
				this.receiverSigRequired == that.receiverSigRequired &&
				Objects.equals(this.proxy, that.proxy) &&
				equalUpToDecodability(this.key, that.key) &&
				Arrays.equals(this.tokenRels, that.tokenRels);
	}

	@Override
	public int hashCode() {
		return Objects.hash(
				key,
				expiry,
				hbarBalance,
				autoRenewSecs,
				senderThreshold,
				receiverThreshold,
				memo,
				deleted,
				smartContract,
				receiverSigRequired,
				proxy,
				Arrays.hashCode(tokenRels));
	}

	/* --- Bean --- */
	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this)
				.add("key", describe(key))
				.add("expiry", expiry)
				.add("balance", hbarBalance)
				.add("autoRenewSecs", autoRenewSecs)
				.add("senderThreshold", senderThreshold)
				.add("receiverThreshold", receiverThreshold)
				.add("memo", memo)
				.add("deleted", deleted)
				.add("smartContract", smartContract)
				.add("receiverSigRequired", receiverSigRequired)
				.add("proxy", proxy)
				.add("tokenRels", Arrays.toString(tokenRels))
				.toString();
	}

	public List<RawTokenRelationship> explicitTokenRels() {
		return IntStream.range(0, numTokenRelationships())
				.mapToObj(i -> new RawTokenRelationship(
						tokenRels[balance(i)],
						tokenRels[num(i)],
						isFrozen(i),
						isKycGranted(i)))
				.collect(toList());
	}

	public String readableTokenRels() {
		var sb = new StringBuilder("[");
		for (int i = 0, n = numTokenRelationships(); i < n; i++) {
			sb.append("0.0.")
					.append(tokenRels[num(i)])
					.append("(balance=")
					.append(tokenRels[balance(i)]);
			if (isFrozen(i)) {
				sb.append(",FROZEN");
			}
			if (isKycGranted(i)) {
				sb.append(",KYC");
			}
			sb.append(")");
			if (i < n - 1) {
				sb.append(", ");
			}
		}
		sb.append("]");

		return sb.toString();
	}

	public ResponseCodeEnum wipeTokenRelationship(TokenID id) {
		int at = logicalIndexOf(id);
		if (at < 0) {
			return ACCOUNT_HAS_NO_TOKEN_RELATIONSHIP;
		} else {
			removeRelationship(at);
		}
		return OK;
	}

	public JKey key() {
		return key;
	}

	public long expiry() {
		return expiry;
	}

	public long balance() {
		return hbarBalance;
	}

	public long autoRenewSecs() {
		return autoRenewSecs;
	}

	public long senderThreshold() {
		return senderThreshold;
	}

	public long receiverThreshold() {
		return receiverThreshold;
	}

	public String memo() {
		return memo;
	}

	public boolean isDeleted() {
		return deleted;
	}

	public boolean isSmartContract() {
		return smartContract;
	}

	public boolean isReceiverSigRequired() {
		return receiverSigRequired;
	}

	public EntityId proxy() {
		return proxy;
	}

	public void setKey(JKey key) {
		this.key = key;
	}

	public void setExpiry(long expiry) {
		this.expiry = expiry;
	}

	public void setHbarBalance(long hbarBalance) {
		this.hbarBalance = hbarBalance;
	}

	public void setAutoRenewSecs(long autoRenewSecs) {
		this.autoRenewSecs = autoRenewSecs;
	}

	public void setSenderThreshold(long senderThreshold) {
		this.senderThreshold = senderThreshold;
	}

	public void setReceiverThreshold(long receiverThreshold) {
		this.receiverThreshold = receiverThreshold;
	}

	public void setMemo(String memo) {
		this.memo = memo;
	}

	public void setDeleted(boolean deleted) {
		this.deleted = deleted;
	}

	public void setSmartContract(boolean smartContract) {
		this.smartContract = smartContract;
	}

	public void setReceiverSigRequired(boolean receiverSigRequired) {
		this.receiverSigRequired = receiverSigRequired;
	}

	public void setProxy(EntityId proxy) {
		this.proxy = proxy;
	}

	long[] getTokenRels() {
		return tokenRels;
	}

	public void setTokenRels(long[] tokenRels) {
		this.tokenRels = tokenRels;
	}

	/* --- Token Manipulation --- */
	public int numTokenRelationships() {
		return tokenRels.length / NUM_TOKEN_PROPS;
	}

	public boolean hasRelationshipWith(TokenID id) {
		return logicalIndexOf(id) >= 0;
	}

	public List<TokenBalance> getAllExplicitTokenBalances() {
		return IntStream.range(0, numTokenRelationships())
				.mapToObj(i -> TokenBalance.newBuilder()
						.setTokenId(TokenID.newBuilder()
								.setShardNum(0)
								.setRealmNum(0)
								.setTokenNum(tokenRels[num(i)]))
						.setBalance(tokenRels[balance(i)]).build())
				.collect(toList());
	}

	public long getTokenBalance(TokenID id) {
		int i = logicalIndexOf(id);
		return (i < 0) ? 0 : tokenRels[balance(i)];
	}

	public boolean isFrozen(TokenID id, MerkleToken token) {
		if (token.freezeKey().isEmpty()) {
			return false;
		}
		int i = logicalIndexOf(id);
		return (i < 0) ? token.accountsAreFrozenByDefault() : isFrozen(i);
	}

	public boolean isKycGranted(TokenID id, MerkleToken token) {
		if (token.kycKey().isEmpty()) {
			return true;
		}
		int i = logicalIndexOf(id);
		return (i < 0) ? token.accountKycGrantedByDefault() : isKycGranted(i);
	}

	public void freeze(TokenID id, MerkleToken token) {
		if (token.freezeKey().isEmpty()) {
			return;
		}
		long defaultForNewRel = token.accountsAreFrozenByDefault()
				? NOOP_MASK
				: FREEZE_MASK | defaultKycMaskFor(token);
		updateFlag(FREEZE_MASK, defaultForNewRel, id, this::set);
	}

	public void unfreeze(TokenID id, MerkleToken token) {
		if (token.freezeKey().isEmpty()) {
			return;
		}
		long defaultForNewRel = !token.accountsAreFrozenByDefault()
				? NOOP_MASK
				: defaultKycMaskFor(token);
		updateFlag(FREEZE_MASK, defaultForNewRel, id, this::unset);
	}

	public void grantKyc(TokenID id, MerkleToken token) {
		if (token.kycKey().isEmpty()) {
			return;
		}
		long defaultForNewRel = token.accountKycGrantedByDefault()
				? NOOP_MASK
				: KYC_MASK | defaultFreezeMaskFor(token);
		updateFlag(KYC_MASK, defaultForNewRel, id, this::set);
	}

	public void revokeKyc(TokenID id, MerkleToken token) {
		if (token.kycKey().isEmpty()) {
			return;
		}
		long defaultForNewRel = !token.accountKycGrantedByDefault()
				? NOOP_MASK
				: defaultFreezeMaskFor(token);
		updateFlag(KYC_MASK, defaultForNewRel, id, this::unset);
	}

	public ResponseCodeEnum validityOfAdjustment(TokenID id, MerkleToken token, long adjustment) {
		int i = logicalIndexOf(id), at = i;
		if (i < 0) {
			if (!token.accountKycGrantedByDefault()) {
				return ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
			}
			if (token.accountsAreFrozenByDefault()) {
				return ACCOUNT_FROZEN_FOR_TOKEN;
			}
			if (adjustment < 0) {
				return INSUFFICIENT_TOKEN_BALANCE;
			}
		} else {
			if (!isKycGranted(at)) {
				return ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
			}
			if (isFrozen(at)) {
				return ACCOUNT_FROZEN_FOR_TOKEN;
			}
			if (tokenRels[balance(at)] + adjustment < 0) {
				return INSUFFICIENT_TOKEN_BALANCE;
			}
		}
		return OK;
	}

	public void adjustTokenBalance(TokenID id, MerkleToken token, long adjustment) {
		int i = logicalIndexOf(id), at = i;
		if (i < 0) {
			if (adjustment < 0) {
				throwBalanceIse(id, adjustment);
			}
			if (!token.accountKycGrantedByDefault()) {
				throwNoKycIse(id);
			}
			if (token.accountsAreFrozenByDefault()) {
				throwFrozenIse(id);
			}
			at = -i - 1;
			insertNewRelationship(id, at, KYC_MASK);
		} else {
			if (!isKycGranted(at)) {
				throwNoKycIse(id);
			}
			if (isFrozen(at)) {
				throwFrozenIse(id);
			}
		}
		int pos = balance(at);
		long newBalance = tokenRels[pos] + adjustment;
		if (newBalance < 0) {
			throwBalanceIse(id, newBalance);
		}
		tokenRels[pos] = newBalance;
	}

	/* --- Helpers --- */
	private void insertNewRelationship(TokenID id, int at, long flags) {
		int newNumTokens = tokenRels.length / NUM_TOKEN_PROPS + 1;

		long[] newTokenRels = new long[newNumTokens * NUM_TOKEN_PROPS];
		if (at != 0) {
			System.arraycopy(tokenRels, 0, newTokenRels, 0, at * NUM_TOKEN_PROPS);
		}

		newTokenRels[num(at)] = id.getTokenNum();
		newTokenRels[flags(at)] = flags;

		if (at != newNumTokens) {
			System.arraycopy(
					tokenRels,
					at * NUM_TOKEN_PROPS,
					newTokenRels,
					(at + 1) * NUM_TOKEN_PROPS,
					(newNumTokens - at - 1) * NUM_TOKEN_PROPS);
		}
		tokenRels = newTokenRels;
	}

	private void removeRelationship(int at) {
		int n;
		if ((n = numTokenRelationships()) == 1) {
			tokenRels = NO_TOKEN_RELATIONSHIPS;
			return;
		}
		long[] newTokenRels = new long[(n - 1) * NUM_TOKEN_PROPS];
		if (at != 0) {
			System.arraycopy(tokenRels, 0, newTokenRels, 0, at * NUM_TOKEN_PROPS);
		}
		if (at < (n - 1)) {
			System.arraycopy(
					tokenRels,
					(at + 1) * NUM_TOKEN_PROPS,
					newTokenRels,
					at * NUM_TOKEN_PROPS,
					(n - at - 1) * NUM_TOKEN_PROPS);
		}
		tokenRels = newTokenRels;
	}

	@FunctionalInterface
	private interface FlagMutator {
		void apply(long mask, int at);
	}

	private void updateFlag(
			long mask,
			long defaultForNewRel,
			TokenID id,
			FlagMutator flagMutator
	) {
		int i = logicalIndexOf(id), at = i;
		if (i < 0) {
			if (defaultForNewRel == NOOP_MASK) {
				return;
			}
			at = -i - 1;
			insertNewRelationship(id, at, defaultForNewRel);
		} else {
			flagMutator.apply(mask, at);
		}
	}

	private void throwFrozenIse(TokenID id) {
		throw new IllegalStateException(String.format(
				"Account frozen for token '%s'!", readableId(id)));
	}

	private void throwNoKycIse(TokenID id) {
		throw new IllegalStateException(String.format(
				"Account KYC has not been granted for token '%s'!", readableId(id)));
	}

	private void throwBalanceIse(TokenID id, long balance) {
		throw new IllegalArgumentException(String.format(
				"Account cannot have balance %d for token '%s'!", balance, readableId(id)));
	}

	private long defaultKycMaskFor(MerkleToken token) {
		var flag = !token.hasKycKey() || token.accountKycGrantedByDefault();
		return flag ? KYC_MASK : 0;
	}

	private long defaultFreezeMaskFor(MerkleToken token) {
		var flag = token.hasFreezeKey() && token.accountsAreFrozenByDefault();
		return flag ? FREEZE_MASK : 0;
	}

	private void set(long mask, int i) {
		tokenRels[flags(i)] |= mask;
	}

	private void unset(long mask, int i) {
		tokenRels[flags(i)] &= ~mask;
	}

	private boolean isFrozen(int i) {
		return (tokenRels[flags(i)] & FREEZE_MASK) == FREEZE_MASK;
	}

	private boolean isKycGranted(int i) {
		return (tokenRels[flags(i)] & KYC_MASK) == KYC_MASK;
	}

	private int num(int i) {
		return i * NUM_TOKEN_PROPS;
	}

	private int balance(int i) {
		return i * NUM_TOKEN_PROPS + BALANCE_OFFSET;
	}

	private int flags(int i) {
		return i * NUM_TOKEN_PROPS + FLAGS_OFFSET;
	}

	int logicalIndexOf(TokenID token) {
		int lo = 0, hi = tokenRels.length / NUM_TOKEN_PROPS - 1;
		long num = token.getTokenNum();
		while (lo <= hi) {
			int mid = (lo + (hi - lo) / NUM_TOKEN_PROPS), i = mid * NUM_TOKEN_PROPS;
			long midNum = tokenRels[i];
			if (midNum == num) {
				return mid;
			} else if (midNum < num) {
				lo = mid + 1;
			} else {
				hi = mid - 1;
			}
		}
		return -(lo + 1);
	}

}
