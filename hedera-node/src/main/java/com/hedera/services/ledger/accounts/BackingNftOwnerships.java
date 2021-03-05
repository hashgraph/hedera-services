package com.hedera.services.ledger.accounts;

import com.google.protobuf.ByteString;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleNftOwnership;
import com.hederahashgraph.api.proto.java.NftID;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static com.hedera.services.ledger.HederaLedger.NFT_ID_COMPARATOR;

public class BackingNftOwnerships implements BackingStore<Pair<NftID, ByteString>, MerkleEntityId> {
	public static final Comparator<Pair<NftID, ByteString>> OWNERSHIP_CMP =
			Comparator.<Pair<NftID, ByteString>, NftID>comparing(Pair::getLeft, NFT_ID_COMPARATOR)
					.thenComparing(Pair::getRight, ByteString.unsignedLexicographicalComparator());
	private static final Comparator<Map.Entry<Pair<NftID, ByteString>, MerkleEntityId>> OWNERSHIP_ENTRY_CMP =
			Comparator.comparing(Map.Entry::getKey, OWNERSHIP_CMP);

	Set<Pair<NftID, ByteString>> existingOwnerships = new HashSet<>();
	Map<Pair<NftID, ByteString>, MerkleEntityId> cache = new HashMap<>();

	private final Supplier<FCMap<MerkleNftOwnership, MerkleEntityId>> delegate;

	public BackingNftOwnerships(Supplier<FCMap<MerkleNftOwnership, MerkleEntityId>> delegate) {
		this.delegate = delegate;
		rebuildFromSources();
	}

	@Override
	public void rebuildFromSources() {
		existingOwnerships.clear();
		delegate.get().keySet().stream()
				.map(MerkleNftOwnership::asPair)
				.forEach(existingOwnerships::add);
	}
	@Override
	public void flushMutableRefs() {
		cache.entrySet().stream()
				.sorted(OWNERSHIP_ENTRY_CMP)
				.forEach(entry ->
						delegate.get().replace(MerkleNftOwnership.fromPair(entry.getKey()), entry.getValue()));
		cache.clear();
	}

	@Override
	public MerkleEntityId getRef(Pair<NftID, ByteString> key) {
		return cache.computeIfAbsent(
				key,
				ignore -> delegate.get().getForModify(MerkleNftOwnership.fromPair(key)));
	}

	@Override
	public MerkleEntityId getUnsafeRef(Pair<NftID, ByteString> key) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void put(Pair<NftID, ByteString> key, MerkleEntityId account) {
		if (!existingOwnerships.contains(key)) {
			delegate.get().put(MerkleNftOwnership.fromPair(key), account);
			existingOwnerships.add(key);
		} else if (!cache.containsKey(key) || cache.get(key) != account) {
			throw new IllegalArgumentException(String.format(
					"Argument 'key=%s' does not map to a mutable ref!",
					MerkleNftOwnership.fromPair(key)));
		}
	}

	@Override
	public void remove(Pair<NftID, ByteString> key) {
		existingOwnerships.remove(key);
		delegate.get().remove(MerkleNftOwnership.fromPair(key));
	}

	@Override
	public boolean contains(Pair<NftID, ByteString> key) {
		return existingOwnerships.contains(key);
	}

	@Override
	public Set<Pair<NftID, ByteString>> idSet() {
		throw new UnsupportedOperationException();
	}

	public static String readableNftOwnership(Pair<NftID, ByteString> key) {
		return MerkleNftOwnership.fromPair(key).toString();
	}
}
