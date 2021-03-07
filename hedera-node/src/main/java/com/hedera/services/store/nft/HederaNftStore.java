package com.hedera.services.store.nft;

import com.google.protobuf.ByteString;
import com.hedera.services.context.TransactionContext;
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

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.hedera.services.ledger.properties.NftOwningAccountProperty.OWNER;
import static com.hedera.services.state.merkle.MerkleEntityId.fromAccountId;
import static com.hedera.services.state.merkle.MerkleEntityId.fromNftId;
import static com.hedera.services.state.submerkle.EntityId.fromGrpcAccount;
import static com.hedera.services.state.submerkle.EntityId.fromGrpcNftType;
import static com.hedera.services.store.CreationResult.failure;
import static com.hedera.services.store.CreationResult.success;
import static com.hedera.services.utils.EntityIdUtils.readableId;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_NOT_ASSOCIATED_TO_NFT_TYPE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_NOT_OWNER_OF_NFT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_SERIAL_NO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NFT_WAS_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class HederaNftStore extends HederaStore implements NftStore {
	private static final Logger log = LogManager.getLogger(HederaNftStore.class);

	static final NftID NO_PENDING_ID = NftID.getDefaultInstance();
	public static final AccountID ZERO_ADDRESS = AccountID.getDefaultInstance();

	private final AcquisitionLogs acquisitionLogs;
	private final TransactionContext txnCtx;
	private final TransactionalLedger<
			Pair<NftID, ByteString>,
			NftOwningAccountProperty,
			MerkleEntityId> nftOwnershipLedger;
	private final Supplier<FCMap<MerkleEntityId, MerkleNftType>> nftTypes;

	NftID pendingId = NO_PENDING_ID;
	MerkleNftType pendingCreation;

	public HederaNftStore(
			EntityIdSource ids,
			AcquisitionLogs acquisitionLogs,
			TransactionContext txnCtx,
			Supplier<FCMap<MerkleEntityId, MerkleNftType>> nftTypes,
			TransactionalLedger<
					Pair<NftID, ByteString>,
					NftOwningAccountProperty,
					MerkleEntityId> nftOwnershipLedger
	) {
		super(ids);
		this.txnCtx = txnCtx;
		this.nftTypes = nftTypes;
		this.acquisitionLogs = acquisitionLogs;
		this.nftOwnershipLedger = nftOwnershipLedger;
	}

	@Override
	public CreationResult<NftID> createProvisionally(NftCreateTransactionBody request, AccountID sponsor, long now) {
		var treasury = request.getTreasury();
		var validity = accountCheck(treasury, INVALID_ACCOUNT_ID);
		if (validity != OK) {
			return failure(validity);
		}

		pendingId = ids.newNftId(sponsor);
		pendingCreation = new MerkleNftType(false, request.getSerialNoCount(), fromGrpcAccount(treasury));
		return success(pendingId);
	}

	@Override
	public ResponseCodeEnum mint(NftID nId, List<ByteString> serialNos) {
		var result = OK;
		if ((result = checkNftTypeUsability(nId)) != OK) {
			return result;
		}

		var nftType = get(nId);
		var treasury = nftType.getTreasury().toGrpcAccountId();
		serialNos.forEach(serialNo -> {
			var nft = Pair.of(nId, serialNo);
			nftOwnershipLedger.create(nft);
			doAcquisition(treasury, ZERO_ADDRESS, nft);
		});

		return result;
	}

	@Override
	public ResponseCodeEnum transferOwnership(NftID nId, ByteString serialNo, AccountID from, AccountID to) {
		var validity = transferValidity(nId, serialNo, from, to);
		if (validity != OK) {
			return validity;
		}
		doAcquisition(to, from, Pair.of(nId, serialNo));
		return OK;
	}

	private void doAcquisition(AccountID to, AccountID from, Pair<NftID, ByteString> nft) {
		nftOwnershipLedger.set(nft, OWNER, to);
		hederaLedger.updateNftXfers(nft.getLeft(), nft.getRight(), from, to);
		acquisitionLogs.logAcquisition(
				fromAccountId(from),
				fromAccountId(to),
				nft,
				txnCtx.consensusTime());
	}

	@Override
	public MerkleNftType get(NftID id) {
		throwIfMissing(id);

		return pendingId.equals(id) ? pendingCreation : nftTypes.get().get(fromNftId(id));
	}

	@Override
	public boolean exists(NftID id) {
		return (isCreationPending() && pendingId.equals(id)) || nftTypes.get().containsKey(fromNftId(id));
	}

	@Override
	public void apply(NftID id, Consumer<MerkleNftType> change) {
		throwIfMissing(id);

		var key = fromNftId(id);
		var token = nftTypes.get().getForModify(key);
		try {
			change.accept(token);
		} catch (Exception internal) {
			throw new IllegalArgumentException("Nft change failed unexpectedly!", internal);
		} finally {
			nftTypes.get().replace(key, token);
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

		nftTypes.get().put(fromNftId(pendingId), pendingCreation);

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

	@Override
	public ResponseCodeEnum associate(AccountID aId, List<NftID> nftTypes) {
		ResponseCodeEnum validity;
		if ((validity = checkAccountExistence(aId)) != OK) {
			return validity;
		}
		for (NftID nId : nftTypes) {
			if ((validity = checkNftTypeUsability(nId)) != OK) {
				return validity;
			}
		}
		var accountNfts = hederaLedger.getAssociatedNfts(aId);
		accountNfts.associateAllNfts(new HashSet<>(nftTypes));
		hederaLedger.setAssociatedNfts(aId, accountNfts);
		return validity;
	}

	private ResponseCodeEnum transferValidity(NftID nId, ByteString serialNo, AccountID from, AccountID to) {
		ResponseCodeEnum validity;
		if ((validity = checkAccountExistence(from)) != OK) {
			return validity;
		}
		if ((validity = checkAccountExistence(to)) != OK) {
			return validity;
		}
		if ((validity = checkNftTypeUsability(nId)) != OK) {
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
		if (!hederaLedger.isAssociatedTo(to, nId)) {
			return ACCOUNT_NOT_ASSOCIATED_TO_NFT_TYPE;
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

	private ResponseCodeEnum checkNftTypeUsability(NftID nId) {
		return exists(nId) ? checkDeletion(nId) : INVALID_NFT_ID;
	}

	private ResponseCodeEnum checkDeletion(NftID nId) {
		return (pendingId == nId || !nftTypes.get().get(fromNftId(nId)).isDeleted()) ? OK : NFT_WAS_DELETED;
	}
}
