package com.hedera.services.ledger.accounts;

import com.hedera.services.state.merkle.MerkleNamedAssociation;
import com.hedera.services.state.merkle.MerklePlaceholder;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static com.hedera.services.ledger.HederaLedger.ACCOUNT_ID_COMPARATOR;
import static com.hedera.services.ledger.HederaLedger.NFT_ID_COMPARATOR;
import static com.hedera.services.state.merkle.MerkleNamedAssociation.fromAccountNftSerialNoOwnership;
import static com.hedera.services.utils.EntityIdUtils.readableId;

public class BackingNftOwnerships implements BackingStore<Triple<AccountID, NftID, String>, MerklePlaceholder> {
	public static final Comparator<Triple<AccountID, NftID, String>> OWNERSHIP_CMP =
			Comparator.<Triple<AccountID, NftID, String>, AccountID>comparing(Triple::getLeft, ACCOUNT_ID_COMPARATOR)
					.thenComparing(Triple::getMiddle, NFT_ID_COMPARATOR)
					.thenComparing(Triple::getRight);
	private static final Comparator<Map.Entry<Triple<AccountID, NftID, String>, MerklePlaceholder>> OWNERSHIP_ENTRY_CMP =
			Comparator.comparing(Map.Entry::getKey, OWNERSHIP_CMP);

	Set<Triple<AccountID, NftID, String>> existingOwnerships = new HashSet<>();
	Map<Triple<AccountID, NftID, String>, MerklePlaceholder> cache = new HashMap<>();

	private final Supplier<FCMap<MerkleNamedAssociation, MerklePlaceholder>> delegate;

	public BackingNftOwnerships(Supplier<FCMap<MerkleNamedAssociation, MerklePlaceholder>> delegate) {
		this.delegate = delegate;
		rebuildFromSources();
	}

	@Override
	public void rebuildFromSources() {
		existingOwnerships.clear();
		delegate.get().keySet().stream()
				.map(MerkleNamedAssociation::asAccountNftSerialNoOwnership)
				.forEach(existingOwnerships::add);
	}
	@Override
	public void flushMutableRefs() {
		cache.entrySet().stream()
				.sorted(OWNERSHIP_ENTRY_CMP)
				.forEach(entry ->
						delegate.get().replace(fromAccountNftSerialNoOwnership(entry.getKey()), entry.getValue()));
		cache.clear();
	}

	@Override
	public MerklePlaceholder getRef(Triple<AccountID, NftID, String> key) {
		return cache.computeIfAbsent(
				key,
				ignore -> delegate.get().getForModify(fromAccountNftSerialNoOwnership(key)));
	}

	@Override
	public MerklePlaceholder getUnsafeRef(Triple<AccountID, NftID, String> id) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void put(Triple<AccountID, NftID, String> key, MerklePlaceholder placeholder) {
		if (!existingOwnerships.contains(key)) {
			delegate.get().put(fromAccountNftSerialNoOwnership(key), placeholder);
			existingOwnerships.add(key);
		} else if (!cache.containsKey(key) || cache.get(key) != placeholder) {
			throw new IllegalArgumentException(String.format(
					"Argument 'key=%s' does not map to a mutable ref!",
					fromAccountNftSerialNoOwnership(key).toAbbrevString()));
		}
	}

	@Override
	public void remove(Triple<AccountID, NftID, String> id) {
		existingOwnerships.remove(id);
		delegate.get().remove(fromAccountNftSerialNoOwnership(id));
	}

	@Override
	public boolean contains(Triple<AccountID, NftID, String> key) {
		return existingOwnerships.contains(key);
	}

	@Override
	public Set<Triple<AccountID, NftID, String>> idSet() {
		throw new UnsupportedOperationException();
	}

	public static String readableNftOwnership(Triple<AccountID, NftID, String> ownership) {
		return fromAccountNftSerialNoOwnership(ownership).toAbbrevString();
	}
}
