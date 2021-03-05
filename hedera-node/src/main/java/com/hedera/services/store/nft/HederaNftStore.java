package com.hedera.services.store.nft;

import com.google.protobuf.ByteString;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.NftOwningAccountProperty;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleNftType;
import com.hedera.services.state.merkle.MerklePlaceholder;
import com.hedera.services.store.CreationResult;
import com.hedera.services.store.HederaStore;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftCreateTransactionBody;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.hedera.services.state.merkle.MerkleEntityId.fromNftId;
import static com.hedera.services.utils.EntityIdUtils.readableId;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class HederaNftStore extends HederaStore implements NftStore {
	private static final Logger log = LogManager.getLogger(HederaNftStore.class);

	static final NftID NO_PENDING_ID = NftID.getDefaultInstance();

	private final Supplier<FCMap<MerkleEntityId, MerkleNftType>> nfts;
	private final TransactionalLedger<
			Pair<NftID, ByteString>,
			NftOwningAccountProperty,
			MerkleEntityId> nftOwnershipLedger;

	NftID pendingId = NO_PENDING_ID;
	MerkleNftType pendingCreation;

	public HederaNftStore(
			EntityIdSource ids,
			Supplier<FCMap<MerkleEntityId, MerkleNftType>> nfts,
			TransactionalLedger<
					Pair<NftID, ByteString>,
					NftOwningAccountProperty,
					MerkleEntityId> nftOwnershipLedger
	) {
		super(ids);
		this.nfts = nfts;
		this.nftOwnershipLedger = nftOwnershipLedger;
	}

	@Override
	public CreationResult<NftID> createProvisionally(NftCreateTransactionBody request, AccountID sponsor, long now) {
		return null;
	}

	@Override
	public ResponseCodeEnum transferOwnership(NftID nft, String serialNo, AccountID from, AccountID to) {
		return null;
	}

	@Override
	public MerkleNftType get(NftID id) {
		throwIfMissing(id);

		return pendingId.equals(id) ? pendingCreation : nfts.get().get(fromNftId(id));
	}

	@Override
	public boolean exists(NftID id) {
		return (isCreationPending() && pendingId.equals(id)) || nfts.get().containsKey(fromNftId(id));
	}

	@Override
	public void apply(NftID id, Consumer<MerkleNftType> change) {
		throwIfMissing(id);

		var key = fromNftId(id);
		var token = nfts.get().getForModify(key);
		try {
			change.accept(token);
		} catch (Exception internal) {
			throw new IllegalArgumentException("Nft change failed unexpectedly!", internal);
		} finally {
			nfts.get().replace(key, token);
		}
	}

	@Override
	public void setHederaLedger(HederaLedger ledger) {
		ledger.setNftOwnershipLedger(nftOwnershipLedger);
		super.setHederaLedger(ledger);
	}

	@Override
	public void commitCreation() {
		throwIfNoCreationPending();

		nfts.get().put(fromNftId(pendingId), pendingCreation);

		resetPendingCreation();
	}

	@Override
	public void rollbackCreation() {
		throwIfNoCreationPending();

		ids.reclaimLastId();
		resetPendingCreation();
	}

	@Override
	public boolean isCreationPending() {
		return pendingId != NO_PENDING_ID;
	}

	@Override
	public ResponseCodeEnum delete(NftID id) {
		throw new UnsupportedOperationException();
	}

	private ResponseCodeEnum sanityChecked(
			AccountID from,
			AccountID to,
			NftID nft,
			String serialNo,
			Function<MerkleNftType, ResponseCodeEnum> action
	) {
		ResponseCodeEnum validity;
		if ((validity = checkAccountExistence(from)) != OK) {
			return validity;
		}
		if ((validity = checkAccountExistence(to)) != OK) {
			return validity;
		}
		if ((validity = (exists(nft) ? OK : INVALID_NFT_ID)) != OK) {
			return validity;
		}
//
//		var token = get(tId);
//		if (token.isDeleted()) {
//			return TOKEN_WAS_DELETED;
//		}
//
//		var key = asTokenRel(aId, tId);
//		if (!tokenRelsLedger.exists(key)) {
//			return TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
//		}
//
//		return action.apply(token);
		return OK;
	}

	private void throwIfMissing(NftID id) {
		if (!exists(id)) {
			throw new IllegalArgumentException(String.format(
					"Argument 'id=%s' does not refer to a known nft!",
					readableId(id)));
		}
	}

	private void throwIfNoCreationPending() {
		if (pendingId == NO_PENDING_ID) {
			throw new IllegalStateException("No pending NFT creation!");
		}
	}

	private void resetPendingCreation() {
		pendingId = NO_PENDING_ID;
		pendingCreation = null;
	}
}
