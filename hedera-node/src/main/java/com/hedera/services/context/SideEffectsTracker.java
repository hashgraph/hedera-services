package com.hedera.services.context;

import com.hedera.services.state.submerkle.FcTokenAssociation;
import com.hedera.services.store.models.NftId;
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
 * Extracts the record-creation logic previously squashed into {@link com.hedera.services.ledger.HederaLedger}.
 *
 * Despite all the well-known opportunities for performance improvements in this implementation, changes nothing...
 *
 * ...yet. 😉
 */
@Singleton
public class SideEffectsTracker {
	private static final int MAX_TOKENS_TOUCHED = 1_000;

	private final TokenID[] tokensTouched = new TokenID[MAX_TOKENS_TOUCHED];
	private final TransferList.Builder netHbarChanges = TransferList.newBuilder();
	private final List<FcTokenAssociation> newTokenAssociations = new ArrayList<>();
	private final Map<TokenID, TransferList.Builder> netTokenChanges = new HashMap<>();
	private final Map<TokenID, TokenTransferList.Builder> nftOwnerChanges = new HashMap<>();

	private int numTouches = 0;

	@Inject
	public SideEffectsTracker() {
		/* For Dagger2 */
	}

	/**
	 * Clears all side effects tracked since the last call to this method.
	 */
	public void reset() {
		netHbarChanges.clear();
		clearTokenChanges();
	}

	/**
	 * Clears all token-related side effects tracked since the last call to this method. These include:
	 * <ul>
	 *  	<li>Changes to balances of fungible token units.</li>
	 *  	<li>Changes to NFT owners.</li>
	 *  	<li>(TODO) Automatically created token associations.</li>
	 * </ul>
	 */
	public void clearTokenChanges() {
		for (int i = 0; i < numTouches; i++) {
			final var fungibleBuilder = netTokenChanges.get(tokensTouched[i]);
			if (fungibleBuilder != null) {
				fungibleBuilder.clearAccountAmounts();
			} else {
				nftOwnerChanges.get(tokensTouched[i]).clearNftTransfers();
			}
		}
		numTouches = 0;
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
	public void hbarChange(final AccountID account, final long amount) {
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
	public void tokenUnitsChange(final TokenID token, final AccountID account, final long amount) {
		tokensTouched[numTouches++] = token;
		final var unitChanges = netTokenChanges.computeIfAbsent(token, __ -> TransferList.newBuilder());
		updateFungibleChanges(account, amount, unitChanges);
	}

	/**
	 * Tracks ownership of the given NFT changing from the given sender to the given receiver. This tracking
	 * does <b>not</b> perform a "transitive closure" over ownership changes; that is, if say NFT {@code 0.0.666.1}
	 * changes ownership twice in the same transaction, once from {@code 0.0.12345} to {@code 0.0.23456}, and
	 * again from {@code 0.0.23456} to {@code 0.0.34567}, then <b>both</b> these ownership changes will be
	 * recorded in the list returned by {@link SideEffectsTracker#computeNetTokenUnitAndOwnershipChanges()}.
	 *
	 * @param nftId
	 * 		the NFT changing hands
	 * @param from
	 * 		the sender of the NFT
	 * @param to
	 * 		the receiver of the NFT
	 */
	public void nftOwnerChange(final NftId nftId, final AccountID from, AccountID to) {
		final var token = nftId.tokenId();
		tokensTouched[numTouches++] = token;
		var xfers = nftOwnerChanges.computeIfAbsent(token, __ -> TokenTransferList.newBuilder());
		xfers.addNftTransfers(nftTransferBuilderWith(from, to, nftId.serialNo()));
	}

	/**
	 * Returns the list of net ℏ balance changes after all incremental side effects tracked since the last
	 * call to {@link SideEffectsTracker#reset()}. The returned list is sorted in ascending order by the
	 * {@link com.hedera.services.ledger.HederaLedger#ACCOUNT_ID_COMPARATOR}.
	 *
	 * @return the ordered net balance changes
	 */
	public TransferList computeNetHbarChanges() {
		purgeZeroAdjustments(netHbarChanges);
		return netHbarChanges.build();
	}

	public List<TokenTransferList> computeNetTokenUnitAndOwnershipChanges() {
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
