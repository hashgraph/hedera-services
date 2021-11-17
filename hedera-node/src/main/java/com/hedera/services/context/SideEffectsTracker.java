package com.hedera.services.context;

import com.hedera.services.state.submerkle.FcTokenAssociation;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.TokenRelationship;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransferList;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hedera.services.ledger.HederaLedger.ACCOUNT_ID_COMPARATOR;
import static com.hedera.services.ledger.HederaLedger.TOKEN_ID_COMPARATOR;

/**
 * Extracts the side-effect tracking logic previously squashed into {@link com.hedera.services.ledger.HederaLedger}
 * and the {@link com.hedera.services.store.TypedTokenStore}. Despite all the well-known opportunities for performance
 * improvements here, this implementation changes nothing...yet. 😉
 */
@Singleton
public class SideEffectsTracker {
	private static final long INAPPLICABLE_NEW_SUPPLY = -1;
	private static final int MAX_TOKENS_TOUCHED = 1_000;

	private final TokenID[] tokensTouched = new TokenID[MAX_TOKENS_TOUCHED];
	private final TransferList.Builder netHbarChanges = TransferList.newBuilder();
	private final List<Long> nftMints = new ArrayList<>();
	private final List<TokenTransferList> explicitNetTokenChanges = null;
	private final List<FcTokenAssociation> autoAssociations = new ArrayList<>();
	private final Map<TokenID, TransferList.Builder> netTokenChanges = new HashMap<>();
	private final Map<TokenID, TokenTransferList.Builder> nftOwnerChanges = new HashMap<>();

	private int numTouches = 0;
	private long newSupply = INAPPLICABLE_NEW_SUPPLY;

	@Inject
	public SideEffectsTracker() {
		/* For Dagger2 */
	}

	/**
	 * Tracks the summarized balance changes (if any) contained in the given token relationships.
	 *
	 * @param changedTokenRels the changed token relationships
	 */
	public void trackSummarizedTokenBalanceChanges(final List<TokenRelationship> changedTokenRels) {

	}

	/**
	 * Tracks the new supply of a token as changed by the active transaction. (Since only
	 * one token's supply can change in a record-level transaction, it is redundant to
	 * mention the type here.)
	 *
	 * @param newSupply the new supply of the token
	 */
	public void trackTokenSupply(final long newSupply) {
		this.newSupply = newSupply;
	}

	/**
	 * Indicates whether there any token supply changes were tracked this transaction.
	 *
	 * @return if any token supply changes were tracked
	 */
	public boolean hasTrackedTokenSupply() {
		return newSupply != INAPPLICABLE_NEW_SUPPLY;
	}

	/**
	 * Returns the token supply change that occurred d during the transaction.
	 *
	 * @return the token supply change
	 */
	public long getTrackedTokenSupply() {
		return newSupply;
	}

	/**
	 * Tracks an NFT serial number minted during the transaction. (Since a record-level
	 * transaction can only mint NFTs of one type, it is redundant to mention more here.)
	 *
	 * @param serialNo
	 * 		the minted NFT
	 */
	public void trackMintedNft(final long serialNo) {
		nftMints.add(serialNo);
	}

	/**
	 * Indicates whether there any NFT mints were tracked this transaction.
	 *
	 * @return if any NFT mints were tracked
	 */
	public boolean hasTrackedNftMints() {
		return !nftMints.isEmpty();
	}

	/**
	 * Returns the list of NFT serial numbers minted during the transaction; these will
	 * be in consecutive ascending order.
	 *
	 * @return the tracked NFT mints
	 */
	public List<Long> getTrackedNftMints() {
		return nftMints;
	}

	/**
	 * Tracks an account/token association automatically created (either by a {@code TokenCreate}
	 * or a {@code CryptoTransfer}).
	 *
	 * @param token
	 * 		the token involved in the auto-association
	 * @param account
	 * 		the account involved in the auto-association
	 */
	public void trackAutoAssociation(final TokenID token, final AccountID account) {
		final var association = new FcTokenAssociation(token.getTokenNum(), account.getAccountNum());
		autoAssociations.add(association);
	}

	/**
	 * Returns the list of automatically created account/token associations, in the order they were
	 * created during the transaction.
	 *
	 * @return the created auto-associations
	 */
	public List<FcTokenAssociation> getTrackedAutoAssociations() {
		return autoAssociations;
	}

	/**
	 * Tracks an incremental ℏ balance change for the given account. It is important to note that each
	 * change is <b>incremental</b>; that is, two consecutive calls {@code hbarChange(0.0.12345, +1)} and
	 * {@code hbarChange(0.0.12345, +2)} are equivalent to a single {@code hbarChange(0.0.12345, +3)} call.
	 *
	 * @param account
	 * 		the changed account
	 * @param amount
	 * 		the incremental ℏ change to track
	 */
	public void trackHbarChange(final AccountID account, final long amount) {
		updateFungibleChanges(account, amount, netHbarChanges);
	}

	/**
	 * Tracks an incremental balance change for the given account in units of the given token. It is important
	 * to note that each change is <b>incremental</b>; that is, two consecutive calls
	 * {@code tokenUnitsChange(0.0.666, 0.0.12345, +1)} and {@code tokenUnitsChange(0.0.666, 0.0.12345, +2)}
	 * are equivalent to a single {@code tokenUnitsChange(0.0.666, 0.0.12345, +3)}  call.
	 *
	 * @param token
	 * 		the denomination of the balance change
	 * @param account
	 * 		the changed account
	 * @param amount
	 * 		the incremental unit change to track
	 */
	public void trackTokenUnitsChange(final TokenID token, final AccountID account, final long amount) {
		tokensTouched[numTouches++] = token;
		final var unitChanges = netTokenChanges.computeIfAbsent(token, ignore -> TransferList.newBuilder());
		updateFungibleChanges(account, amount, unitChanges);
	}


	/**
	 * Tracks ownership of the given NFT changing from the given sender to the given receiver. This tracking
	 * does <b>not</b> perform a "transitive closure" over ownership changes; that is, if say NFT {@code 0.0.666.1}
	 * changes ownership twice in the same transaction, once from {@code 0.0.12345} to {@code 0.0.23456}, and
	 * again from {@code 0.0.23456} to {@code 0.0.34567}, then <b>both</b> these ownership changes will be
	 * recorded in the list returned by {@link SideEffectsTracker#getNetTrackedTokenUnitAndOwnershipChanges()}.
	 *
	 * @param nftId
	 * 		the NFT changing hands
	 * @param from
	 * 		the sender of the NFT
	 * @param to
	 * 		the receiver of the NFT
	 */
	public void trackNftOwnerChange(final NftId nftId, final AccountID from, AccountID to) {
		final var token = nftId.tokenId();
		tokensTouched[numTouches++] = token;
		var xfers = nftOwnerChanges.computeIfAbsent(token, ignore -> TokenTransferList.newBuilder());
		xfers.addNftTransfers(nftTransferBuilderWith(from, to, nftId.serialNo()));
	}

	/**
	 * Returns the list of net ℏ balance changes including all incremental side effects tracked since the
	 * last call to {@link SideEffectsTracker#reset()}. The returned list is sorted in ascending order by
	 * the {@link com.hedera.services.ledger.HederaLedger#ACCOUNT_ID_COMPARATOR}.
	 *
	 * @return the ordered net balance changes
	 */
	public TransferList getNetTrackedHbarChanges() {
		purgeZeroAdjustments(netHbarChanges);
		return netHbarChanges.build();
	}

	/**
	 * Returns the list-of-lists of net token changes (in unit balances for fungible token types, NFT
	 * ownership changes for non-fungible), including all incremental side effects since the last call
	 * to {@link SideEffectsTracker#reset()}. The outer list is sorted in ascending order by the
	 * {@link com.hedera.services.ledger.HederaLedger#TOKEN_ID_COMPARATOR}. Inner lists that represent
	 * changes in fungible unit balances are sorted in ascending order by the
	 * {@link com.hedera.services.ledger.HederaLedger#ACCOUNT_ID_COMPARATOR}; while inner lists that
	 * represent NFT ownership changes are in the order the NFTs were exchanged in the transaction.
	 *
	 * @return the ordered list of ordered balance and NFT ownership changes
	 */
	public List<TokenTransferList> getNetTrackedTokenUnitAndOwnershipChanges() {
		if (numTouches == 0) {
			return Collections.emptyList();
		}
		final List<TokenTransferList> all = new ArrayList<>();
		Arrays.sort(tokensTouched, 0, numTouches, TOKEN_ID_COMPARATOR);
		for (int i = 0; i < numTouches; i++) {
			var token = tokensTouched[i];
			if (i == 0 || !token.equals(tokensTouched[i - 1])) {
				final var uniqueTransfersHere = nftOwnerChanges.get(token);
				if (uniqueTransfersHere != null) {
					all.add(TokenTransferList.newBuilder()
							.setToken(token)
							.addAllNftTransfers(uniqueTransfersHere.getNftTransfersList())
							.build());
				} else {
					final var fungibleTransfersHere = netTokenChanges.get(token);
					if (fungibleTransfersHere != null) {
						purgeZeroAdjustments(fungibleTransfersHere);
						all.add(TokenTransferList.newBuilder()
								.setToken(token)
								.addAllTransfers(fungibleTransfersHere.getAccountAmountsList())
								.build());
					}
				}
			}
		}
		return all;
	}

	/**
	 * Clears all side effects tracked since the last call to this method.
	 */
	public void reset() {
		resetTrackedTokenChanges();
		netHbarChanges.clear();
	}

	/**
	 * Clears all token-related side effects tracked since the last call to this method. These include:
	 * <ul>
	 *  	<li>Changes to balances of fungible token units.</li>
	 *  	<li>Changes to NFT owners.</li>
	 *  	<li>Automatically created token associations.</li>
	 * </ul>
	 */
	public void resetTrackedTokenChanges() {
		for (int i = 0; i < numTouches; i++) {
			final var fungibleBuilder = netTokenChanges.get(tokensTouched[i]);
			if (fungibleBuilder != null) {
				fungibleBuilder.clearAccountAmounts();
			} else {
				nftOwnerChanges.get(tokensTouched[i]).clearNftTransfers();
			}
		}
		numTouches = 0;

		newSupply = INAPPLICABLE_NEW_SUPPLY;
		nftMints.clear();
		autoAssociations.clear();
	}

	/* --- Internal helpers --- */
	private void updateFungibleChanges(final AccountID account, final long amount, final TransferList.Builder builder) {
		int loc = 0;
		int diff = -1;
		final var changes = builder.getAccountAmountsBuilderList();
		for (; loc < changes.size(); loc++) {
			diff = ACCOUNT_ID_COMPARATOR.compare(account, changes.get(loc).getAccountID());
			if (diff <= 0) {
				break;
			}
		}
		if (diff == 0) {
			final var change = changes.get(loc);
			final var current = change.getAmount();
			change.setAmount(current + amount);
		} else {
			if (loc == changes.size()) {
				builder.addAccountAmounts(aaBuilderWith(account, amount));
			} else {
				builder.addAccountAmounts(loc, aaBuilderWith(account, amount));
			}
		}
	}

	private void purgeZeroAdjustments(final TransferList.Builder changes) {
		int lastZeroRemoved;
		do {
			lastZeroRemoved = -1;
			for (int i = 0; i < changes.getAccountAmountsCount(); i++) {
				if (changes.getAccountAmounts(i).getAmount() == 0) {
					changes.removeAccountAmounts(i);
					lastZeroRemoved = i;
					break;
				}
			}
		} while (lastZeroRemoved != -1);
	}

	private NftTransfer.Builder nftTransferBuilderWith(
			final AccountID senderId,
			final AccountID receiverId,
			final long serialNumber
	) {
		return NftTransfer.newBuilder()
				.setSenderAccountID(senderId)
				.setReceiverAccountID(receiverId)
				.setSerialNumber(serialNumber);
	}

	private AccountAmount.Builder aaBuilderWith(final AccountID account, final long amount) {
		return AccountAmount.newBuilder().setAccountID(account).setAmount(amount);
	}
}
