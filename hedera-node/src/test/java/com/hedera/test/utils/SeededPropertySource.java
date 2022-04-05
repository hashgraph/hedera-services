package com.hedera.test.utils;

import com.google.common.primitives.Longs;
import com.google.protobuf.ByteString;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.legacy.core.jproto.JDelegatableContractAliasKey;
import com.hedera.services.legacy.core.jproto.JECDSASecp256k1Key;
import com.hedera.services.legacy.core.jproto.JEd25519Key;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.state.merkle.internals.BitPackUtils;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcTokenAllowanceId;
import com.hedera.services.state.submerkle.RichInstant;
import com.hedera.services.state.submerkle.TxnId;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.EntityNumPair;
import com.hederahashgraph.api.proto.java.ContractID;
import com.swirlds.common.CommonUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.IntStream;

import static com.hedera.services.state.merkle.internals.BitPackUtils.numFromCode;

public class SeededPropertySource {
	private static final long DEFAULT_SEED = 4_242_424L;

	private final SplittableRandom SEEDED_RANDOM;

	public SeededPropertySource() {
		this.SEEDED_RANDOM = new SplittableRandom(DEFAULT_SEED);
	}

	public SeededPropertySource(final SplittableRandom SEEDED_RANDOM) {
		this.SEEDED_RANDOM = SEEDED_RANDOM;
	}

	public boolean nextBoolean() {
		return SEEDED_RANDOM.nextBoolean();
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
