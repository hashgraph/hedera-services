package com.hedera.test.utils;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;
import com.hedera.services.context.properties.EntityType;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.legacy.core.jproto.JDelegatableContractAliasKey;
import com.hedera.services.legacy.core.jproto.JECDSASecp256k1Key;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.internals.BitPackUtils;
import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.EvmFnResult;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.hedera.services.state.submerkle.FcAssessedCustomFee;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.state.submerkle.FcTokenAssociation;
import com.hedera.services.state.submerkle.NftAdjustments;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.submerkle.SequenceNumber;
import com.hedera.services.state.submerkle.TxnId;
import com.hedera.services.throttles.DeterministicThrottle;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.swirlds.common.CommonUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.IntStream;

import static com.hedera.services.state.merkle.internals.BitPackUtils.numFromCode;

public class SeededPropertySource {
	private static final long BASE_SEED = 4_242_424L;

	private final SplittableRandom SEEDED_RANDOM;

	public SeededPropertySource(final SplittableRandom SEEDED_RANDOM) {
		this.SEEDED_RANDOM = SEEDED_RANDOM;
	}

	public static SeededPropertySource forSerdeTest(final int version, final int testCaseNo) {
		return new SeededPropertySource(new SplittableRandom(version * BASE_SEED + testCaseNo));
	}

	public MerkleNetworkContext nextNetworkContext() {
		final var numThrottles = 5;
		final var seeded = new MerkleNetworkContext();
		seeded.setConsensusTimeOfLastHandledTxn(nextNullableInstant());
		seeded.setSeqNo(nextSeqNo());
		seeded.updateLastScannedEntity(nextInRangeLong());
		seeded.setMidnightRates(nextExchangeRates());
		seeded.setUsageSnapshots(nextUsageSnapshots(numThrottles));
		seeded.setGasThrottleUsageSnapshot(nextUsageSnapshot());
		seeded.setCongestionLevelStarts(nextNullableInstants(numThrottles));
		seeded.setStateVersion(nextUnsignedInt());
		seeded.updateAutoRenewSummaryCounts(nextUnsignedInt(), nextUnsignedInt());
		seeded.setLastMidnightBoundaryCheck(nextNullableInstant());
		seeded.setPreparedUpdateFileNum(nextInRangeLong());
		seeded.setPreparedUpdateFileHash(nextBytes(48));
		seeded.setMigrationRecordsStreamed(nextBoolean());
		return seeded;
	}

	public Instant[] nextNullableInstants(final int n) {
		return IntStream.range(0, n)
				.mapToObj(i -> nextNullableInstant())
				.toArray(Instant[]::new);
	}

	public DeterministicThrottle.UsageSnapshot[] nextUsageSnapshots(final int n) {
		return IntStream.range(0, n)
				.mapToObj(i -> nextUsageSnapshot())
				.toArray(DeterministicThrottle.UsageSnapshot[]::new);
	}

	public DeterministicThrottle.UsageSnapshot nextUsageSnapshot() {
		return new DeterministicThrottle.UsageSnapshot(nextUnsignedLong(), nextNullableInstant());
	}

	public SequenceNumber nextSeqNo() {
		return new SequenceNumber(nextInRangeLong());
	}

	public boolean nextBoolean() {
		return SEEDED_RANDOM.nextBoolean();
	}

	public List<FcAssessedCustomFee> nextAssessedFeesList() {
		final var numFees = nextNonZeroInt(10);
		final List<FcAssessedCustomFee> ans = new ArrayList<>();
		for (int i = 0; i < numFees; i++) {
			ans.add(nextAssessedFee());
		}
		return ans;
	}

	public List<FcTokenAssociation> nextTokenAssociationsList() {
		final var numAssociations = nextNonZeroInt(10);
		final List<FcTokenAssociation> ans = new ArrayList<>();
		for (int i = 0; i < numAssociations; i++) {
			ans.add(nextTokenAssociation());
		}
		return ans;
	}

	public FcTokenAssociation nextTokenAssociation() {
		return new FcTokenAssociation(nextInRangeLong(), nextInRangeLong());
	}

	public FcAssessedCustomFee nextAssessedFee() {
		if (nextBoolean()) {
			return new FcAssessedCustomFee(
					nextEntityId(), nextUnsignedLong(), nextInRangeLongs(nextNonZeroInt(3)));
		} else {
			return new FcAssessedCustomFee(
					nextEntityId(), nextEntityId(), nextUnsignedLong(), nextInRangeLongs(nextNonZeroInt(3)));
		}
	}

	public void nextTokenAdjustmentsIn(final ExpirableTxnRecord.Builder builder) {
		final var numAdjustments = nextNonZeroInt(10);
		final List<EntityId> tokenTypes = new ArrayList<>(numAdjustments);
		final List<NftAdjustments> ownershipChanges = new ArrayList<>(numAdjustments);
		final List<CurrencyAdjustments> fungibleAdjustments = new ArrayList<>(numAdjustments);
		for (int i = 0; i < numAdjustments; i++) {
			tokenTypes.add(nextEntityId());
			ownershipChanges.add(nextOwnershipChanges());
			fungibleAdjustments.add(nextCurrencyAdjustments());
		}
		builder.setTokens(tokenTypes);
		builder.setNftTokenAdjustments(ownershipChanges);
		builder.setTokenAdjustments(fungibleAdjustments);
	}

	public NftAdjustments nextOwnershipChanges() {
		final var numOwnershipChanges = nextNonZeroInt(10);
		final List<EntityId> senders = new ArrayList<>(numOwnershipChanges);
		final List<EntityId> receivers = new ArrayList<>(numOwnershipChanges);
		final long[] serialNos = nextUnsignedLongs(numOwnershipChanges);
		return new NftAdjustments(serialNos, senders, receivers);
	}

	public int nextNonce() {
		return SEEDED_RANDOM.nextInt(1000);
	}

	public int nextInt() {
		return SEEDED_RANDOM.nextInt();
	}

	public TxnId nextTxnId() {
		return new TxnId(nextEntityId(), nextRichInstant(), nextBoolean(), nextNonce());
	}

	public Instant nextInstant() {
		return Instant.ofEpochSecond(nextInRangeLong(), SEEDED_RANDOM.nextInt(1_000_000));
	}

	public Instant nextNullableInstant() {
		return nextBoolean()
				? null
				: Instant.ofEpochSecond(nextInRangeLong(), SEEDED_RANDOM.nextInt(1_000_000));
	}

	public TxnId nextScheduledTxnId() {
		return new TxnId(nextEntityId(), nextRichInstant(), true, 0);
	}

	public CurrencyAdjustments nextCurrencyAdjustments() {
		final var numAdjustments = nextNonZeroInt(10);
		final var ids = nextInRangeLongs(numAdjustments);
		final var amounts = nextLongs(numAdjustments);
		return new CurrencyAdjustments(amounts, ids);
	}

	public EvmFnResult nextEvmFnResult() {
		return new EvmFnResult(
				nextEntityId(),
				nextBytes(128),
				nextString(32),
				nextBytes(32),
				nextUnsignedLong(),
				List.of(),
				List.of(),
				nextBytes(20),
				nextStateChanges(5, 10),
				nextUnsignedLong(),
				nextUnsignedLong(),
				nextBytes(64));
	}

	public Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> nextStateChanges(int n, final int changesPerAddress) {
		final Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> ans = new TreeMap<>();
		while (n-- > 0) {
			final var address = nextAddress();
			final Map<Bytes, Pair<Bytes, Bytes>> changes = new TreeMap<>();
			for (int i = 0; i < changesPerAddress; i++) {
				changes.put(nextEvmWord(), nextStateChangePair());
			}
			ans.put(address, changes);
		}
		return ans;
	}

	public Pair<Bytes, Bytes> nextStateChangePair() {
		if (nextBoolean()) {
			return Pair.of(nextEvmWord(), null);
		} else {
			return Pair.of(nextEvmWord(), nextEvmWord());
		}
	}

	public TxnReceipt nextReceipt() {
		return nextReceiptBuilder().build();
	}

	public TxnReceipt.Builder nextReceiptBuilder() {
		final var builder = TxnReceipt.newBuilder();
		if (nextBoolean()) {
			final var creationType = nextEntityType();
			switch (creationType) {
				case ACCOUNT -> builder.setAccountId(nextEntityId());
				case CONTRACT -> builder.setContractId(nextEntityId());
				case FILE -> builder.setFileId(nextEntityId());
				case SCHEDULE -> {
					builder.setScheduleId(nextEntityId());
					builder.setScheduledTxnId(nextScheduledTxnId());
				}
				case TOKEN -> {
					builder.setTokenId(nextEntityId());
					if (nextBoolean()) {
						builder.setNewTotalSupply(nextUnsignedLong());
					} else if (nextBoolean()) {
						builder.setSerialNumbers(nextInRangeLongs(nextNonZeroInt(10)));
					}
				}
				case TOPIC -> {
					builder.setTopicId(nextEntityId());
					if (nextBoolean()) {
						builder.setRunningHashVersion(nextUnsignedLong());
						builder.setTopicRunningHash(nextBytes(48));
						builder.setTopicSequenceNumber(nextUnsignedLong());
					}
				}
			}
		}
		return builder
				.setExchangeRates(nextExchangeRates())
				.setStatus(nextStatus().toString());
	}

	public int nextNonZeroInt(final int inclusiveUpperBound) {
		return 1 + SEEDED_RANDOM.nextInt(inclusiveUpperBound);
	}

	public ResponseCodeEnum nextStatus() {
		final var choices = ResponseCodeEnum.class.getEnumConstants();
		return choices[SEEDED_RANDOM.nextInt(choices.length)];
	}

	public ExchangeRates nextExchangeRates() {
		return new ExchangeRates(
				nextUnsignedInt(), nextUnsignedInt(), nextUnsignedLong(),
				nextUnsignedInt(), nextUnsignedInt(), nextUnsignedLong());
	}

	public EntityType nextEntityType() {
		final var choices = EntityType.class.getEnumConstants();
		return choices[SEEDED_RANDOM.nextInt(choices.length)];
	}

	public Map<EntityNum, Map<FcTokenAllowanceId, Long>> nextFungibleAllowances(
			final int numUniqueAccounts,
			final int numUniqueTokens,
			final int numSpenders,
			final int numAllowancesPerSpender
	) {
		final EntityNum[] accounts = nextNums(numUniqueAccounts);
		final EntityNum[] tokens = nextNums(numUniqueTokens);

		final Map<EntityNum, Map<FcTokenAllowanceId, Long>> ans = new TreeMap<>();
		for (int i = 0; i < numSpenders; i++) {
			final var aNum = accounts[SEEDED_RANDOM.nextInt(accounts.length)];
			final var allowances = ans.computeIfAbsent(aNum, a -> new TreeMap<>());
			for (int j = 0; j < numAllowancesPerSpender; j++) {
				final var bNum = accounts[SEEDED_RANDOM.nextInt(accounts.length)];
				final var tNum = tokens[SEEDED_RANDOM.nextInt(tokens.length)];
				final var key = new FcTokenAllowanceId(tNum, bNum);
				allowances.put(key, nextUnsignedLong());
			}
		}
		return ans;
	}

	public Map<EntityNum, Map<EntityNum, Long>> nextCryptoAllowances(
			final int numUniqueAccounts,
			final int numSpenders,
			final int numAllowancesPerSpender
	) {
		final EntityNum[] accounts = IntStream.range(0, numUniqueAccounts)
				.mapToObj(i -> nextNum())
				.distinct()
				.toArray(EntityNum[]::new);
		final Map<EntityNum, Map<EntityNum, Long>> ans = new TreeMap<>();
		for (int i = 0; i < numSpenders; i++) {
			final var aNum = accounts[SEEDED_RANDOM.nextInt(accounts.length)];
			final var allowances = ans.computeIfAbsent(aNum, a -> new TreeMap<>());
			for (int j = 0; j < numAllowancesPerSpender; j++) {
				final var bNum = accounts[SEEDED_RANDOM.nextInt(accounts.length)];
				allowances.put(bNum, nextUnsignedLong());
			}
		}
		return ans;
	}

	public Set<FcTokenAllowanceId> nextApprovedForAllAllowances(final int n) {
		final Set<FcTokenAllowanceId> ans = new TreeSet<>();
		for (int i = 0; i < n; i++) {
			ans.add(nextAllowanceId());
		}
		return ans;
	}

	public Map<FcTokenAllowanceId, Long> nextGrantedFungibleAllowances(final int n) {
		final Map<FcTokenAllowanceId, Long> ans = new TreeMap<>();
		for (int i = 0; i < n; i++) {
			ans.put(nextAllowanceId(), nextUnsignedLong());
		}
		return ans;
	}

	public Map<EntityNum, Long> nextGrantedCryptoAllowances(final int n) {
		final Map<EntityNum, Long> ans = new TreeMap<>();
		for (int i = 0; i < n; i++) {
			ans.put(nextNum(), nextUnsignedLong());
		}
		return ans;
	}

	public FcTokenAllowanceId nextAllowanceId() {
		return new FcTokenAllowanceId(nextNum(), nextNum());
	}

	public EntityNum[] nextNums(final int n) {
		return IntStream.range(0, n)
				.mapToObj(i -> nextNum())
				.distinct()
				.toArray(EntityNum[]::new);
	}

	public long[] nextInRangeLongs(final int n) {
		return IntStream.range(0, n)
				.mapToLong(i -> nextInRangeLong())
				.toArray();
	}

	public long[] nextLongs(final int n) {
		return IntStream.range(0, n)
				.mapToLong(i -> nextLong())
				.toArray();
	}

	public long[] nextUnsignedLongs(final int n) {
		return IntStream.range(0, n)
				.mapToLong(i -> nextUnsignedLong())
				.toArray();
	}

	public EntityNum nextNum() {
		return EntityNum.fromLong(SEEDED_RANDOM.nextLong(BitPackUtils.MAX_NUM_ALLOWED));
	}

	public long nextInRangeLong() {
		return numFromCode(SEEDED_RANDOM.nextInt());
	}

	public EntityNumPair nextPair() {
		return EntityNumPair.fromLongs(nextInRangeLong(), nextInRangeLong());
	}

	public long nextUnsignedLong() {
		return SEEDED_RANDOM.nextLong(Long.MAX_VALUE);
	}

	public long nextLong() {
		return SEEDED_RANDOM.nextLong();
	}

	public Bytes nextEvmWord() {
		if (nextBoolean()) {
			return Bytes.ofUnsignedLong(nextLong()).trimLeadingZeros();
		} else {
			return Bytes.wrap(nextBytes(32)).trimLeadingZeros();
		}
	}

	public Address nextAddress() {
		final byte[] ans = new byte[20];
		if (nextBoolean()) {
			SEEDED_RANDOM.nextBytes(ans);
		} else {
			System.arraycopy(Longs.toByteArray(nextInRangeLong()), 0, ans, 12, 8);
		}
		return Address.fromHexString(CommonUtils.hex(ans));
	}

	public int nextUnsignedInt() {
		return SEEDED_RANDOM.nextInt(Integer.MAX_VALUE);
	}

	public short nextUnsignedShort() {
		return (short) SEEDED_RANDOM.nextInt(Short.MAX_VALUE);
	}

	public String nextString(final int len) {
		final var sb = new StringBuilder();
		for (int i = 0; i < len; i++) {
			sb.append((char) SEEDED_RANDOM.nextInt(0x7f));
		}
		return sb.toString();
	}

	public JKey nextKey() {
		final var keyType = SEEDED_RANDOM.nextInt(5);
		if (keyType == 0) {
			return nextEd25519Key();
		} else if (keyType == 1) {
			return nextSecp256k1Key();
		} else if (keyType == 2) {
			return new JContractIDKey(nextContractId());
		} else if (keyType == 3) {
			return new JDelegatableContractAliasKey(nextContractId().toBuilder()
					.clearContractNum()
					.setEvmAddress(nextByteString(20))
					.build());
		} else {
			return new JKeyList(List.of(nextKey(), nextKey()));
		}
	}

	public JKeyList nextKeyList(final int n) {
		return new JKeyList(IntStream.range(0, n).mapToObj(i -> nextKey()).toList());
	}

	public JKey nextEd25519Key() {
		return new JEd25519Key(nextBytes(32));
	}

	public JKey nextSecp256k1Key() {
		return new JECDSASecp256k1Key(nextBytes(33));
	}

	public byte[] nextBytes(final int n) {
		final var ans = new byte[n];
		SEEDED_RANDOM.nextBytes(ans);
		return ans;
	}

	public ByteString nextByteString(final int n) {
		return ByteString.copyFrom(nextBytes(n));
	}

	public EntityId nextEntityId() {
		return new EntityId(nextUnsignedLong(), nextUnsignedLong(), nextUnsignedLong());
	}

	public RichInstant nextRichInstant() {
		return new RichInstant(nextUnsignedLong(), SEEDED_RANDOM.nextInt(1_000_000));
	}

	public ContractID nextContractId() {
		return ContractID.newBuilder()
				.setShardNum(nextUnsignedLong())
				.setRealmNum(nextUnsignedLong())
				.setContractNum(nextUnsignedLong())
				.build();
	}
}
