package com.hedera.services.store.nft;

import com.google.protobuf.ByteString;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.NftOwningAccountProperty;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleNftType;
import com.hedera.services.store.CreationResult;
import com.hedera.services.store.HederaStore;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftCreateTransactionBody;
import com.hederahashgraph.api.proto.java.NftID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.swirlds.fcmap.FCMap;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.hedera.services.ledger.properties.NftOwningAccountProperty.OWNER;
import static com.hedera.services.state.merkle.MerkleEntityId.fromNftId;
import static com.hedera.services.utils.EntityIdUtils.readableId;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_NOT_OWNER_OF_NFT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_SERIAL_NO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NFT_WAS_DELETED;
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
	public ResponseCodeEnum transferOwnership(NftID nId, ByteString serialNo, AccountID from, AccountID to) {
		var validity = transferValidity(nId, serialNo, from, to);
		if (validity != OK) {
			return validity;
		}
		var key = Pair.of(nId, serialNo);
		nftOwnershipLedger.set(key, OWNER, to);
		hederaLedger.updateNftXfers(nId, serialNo, from, to);
		return OK;
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

	private ResponseCodeEnum transferValidity(NftID nId, ByteString serialNo, AccountID from, AccountID to) {
		ResponseCodeEnum validity;
		if ((validity = checkAccountExistence(from)) != OK) {
			return validity;
		}
		if ((validity = checkAccountExistence(to)) != OK) {
			return validity;
		}
		if ((validity = (exists(nId) ? OK : INVALID_NFT_ID)) != OK) {
			return validity;
		}

		var nftType = get(nId);
		if (nftType.isDeleted()) {
			return NFT_WAS_DELETED;
		}

		var key = Pair.of(nId, serialNo);
		if (!nftOwnershipLedger.exists(key)) {
			return INVALID_NFT_SERIAL_NO;
		}
		var owner = nftOwnershipLedger.get(key);
		if (!owner.toAccountId().equals(from)) {
			return ACCOUNT_NOT_OWNER_OF_NFT;
		}

		return OK;
	}

	private void throwIfMissing(NftID id) {
		if (!exists(id)) {
			throw new IllegalArgumentException(String.format(
					"Argument 'id=%s' does not refer to a known NFT!",
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
