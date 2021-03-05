package com.hedera.services.ledger.accounts;

import com.hedera.services.state.merkle.MerkleEntityAssociation;
import com.hedera.services.state.merkle.MerklePlaceholder;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
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
import static com.hedera.services.ledger.HederaLedger.TOKEN_ID_COMPARATOR;

public class BackingNftOwnerships implements BackingStore<Triple<AccountID, NftID, String>, MerklePlaceholder> {
	public static final Comparator<Triple<AccountID, NftID, String>> OWNERSHIP_CMP =
			Comparator.<Triple<AccountID, NftID, String>, AccountID>comparing(Triple::getLeft, ACCOUNT_ID_COMPARATOR)
					.thenComparing(Triple::getMiddle, NFT_ID_COMPARATOR)
					.thenComparing(Triple::getRight);
	private static final Comparator<Map.Entry<Triple<AccountID, NftID, String>, MerklePlaceholder>> OWNERSHIP_ENTRY_CMP =
			Comparator.comparing(Map.Entry::getKey, OWNERSHIP_CMP);

	Set<Triple<AccountID, NftID, String>> existingOwnerships = new HashSet<>();
	Map<Triple<AccountID, NftID, String>, MerklePlaceholder> cache = new HashMap<>();

//	private final Supplier<FCMap<MerkleEntityAssociation, MerkleTokenRelStatus>> delegate;

	@Override
	public void flushMutableRefs() {

	}

	@Override
	public MerklePlaceholder getRef(Triple<AccountID, NftID, String> id) {
		return null;
	}

	@Override
	public MerklePlaceholder getUnsafeRef(Triple<AccountID, NftID, String> id) {
		return null;
	}

	@Override
	public void put(Triple<AccountID, NftID, String> id, MerklePlaceholder account) {

	}

	@Override
	public void remove(Triple<AccountID, NftID, String> id) {

	}

	@Override
	public boolean contains(Triple<AccountID, NftID, String> id) {
		return false;
	}

	@Override
	public Set<Triple<AccountID, NftID, String>> idSet() {
		return null;
	}
}
