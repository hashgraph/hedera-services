/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.context;

import static com.hedera.services.ledger.HederaLedger.ACCOUNT_ID_COMPARATOR;
import static com.hedera.services.ledger.HederaLedger.TOKEN_ID_COMPARATOR;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import com.hedera.services.state.submerkle.CurrencyAdjustments;
import com.hedera.services.state.submerkle.FcTokenAssociation;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.models.OwnershipTracker;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.TokenRelationship;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransferList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.hyperledger.besu.datatypes.Address;

/**
 * Extracts the side-effect tracking logic previously squashed into {@link
 * com.hedera.services.ledger.HederaLedger} and the {@link
 * com.hedera.services.store.TypedTokenStore}. Despite all the well-known opportunities for
 * performance improvements here, this implementation changes nothing...yet. 😉
 */
@Singleton
public class SideEffectsTracker {
    private static final long INAPPLICABLE_NEW_SUPPLY = -1;
    public static final int MISSING_NUMBER = -1;
    private static final int MAX_TOKENS_TOUCHED = 1_000;
    private static final int MAX_BALANCE_CHANGES = 2048;
    public static final int MAX_PSEUDORANDOM_BYTES_LENGTH = 48;
    private final TokenID[] tokensTouched = new TokenID[MAX_TOKENS_TOUCHED];
    private final long[] changedAccounts = new long[MAX_BALANCE_CHANGES];
    private final long[] balanceChanges = new long[MAX_BALANCE_CHANGES];
    private final long[] rewardedAccounts = new long[MAX_BALANCE_CHANGES];
    private final long[] rewardAmounts = new long[MAX_BALANCE_CHANGES];
    private final List<Long> nftMints = new ArrayList<>();
    private final List<FcTokenAssociation> autoAssociations = new ArrayList<>();
    private final Map<TokenID, TransferList.Builder> netTokenChanges = new HashMap<>();
    private final Map<TokenID, TokenTransferList.Builder> nftOwnerChanges = new HashMap<>();

    private int numTokenChangesSoFar = 0;
    private long newSupply = INAPPLICABLE_NEW_SUPPLY;
    private long netHbarChange = 0;
    private int numHbarChangesSoFar = 0;
    private int numRewardedAccounts = 0;
    private TokenID newTokenId = null;
    private AccountID newAccountId = null;
    private ContractID newContractId = null;
    // Either the key-derived alias for an auto-created account, or the EVM address of a created
    // contract
    private ByteString newEntityAlias = ByteString.EMPTY;
    private List<TokenTransferList> explicitNetTokenUnitOrOwnershipChanges = null;

    private byte[] pseudorandomBytes = null;
    private int pseudorandomNumber = MISSING_NUMBER;
    private AccountID hollowAccountId = null;

    @Inject
    public SideEffectsTracker() {
        /* For Dagger2 */
    }

    public void trackNewContract(final ContractID contractId, final Address evmAddress) {
        newContractId = contractId;
        newEntityAlias = ByteString.copyFrom(evmAddress.toArrayUnsafe());
    }

    public void trackAutoCreation(final AccountID accountID, final ByteString alias) {
        this.newAccountId = accountID;
        this.newEntityAlias = alias;
    }

    public void trackHollowAccountUpdate(final AccountID accountID) {
        this.hollowAccountId = accountID;
    }

    /**
     * Tracks the side-effects to a token changed by the active transaction that are of interest to
     * a records historian.
     *
     * @param changedToken a model of the changed token
     */
    public void trackTokenChanges(final Token changedToken) {
        if (changedToken.isNew()) {
            newTokenId = changedToken.getId().asGrpcToken();
        }
        if (changedToken.hasChangedSupply()) {
            newSupply = changedToken.getTotalSupply();
        }
        if (changedToken.hasMintedUniqueTokens()) {
            for (final var nft : changedToken.mintedUniqueTokens()) {
                nftMints.add(nft.getSerialNumber());
            }
        }
    }

    /**
     * Tracks the summarized NFT ownership changes (if any) contained in the given token
     * relationships.
     *
     * @param changedOwners the changed ownerships
     */
    public void trackTokenOwnershipChanges(final OwnershipTracker changedOwners) {
        if (changedOwners.isEmpty()) {
            return;
        }

        final var changes = changedOwners.getChanges();
        final var changedIds = new ArrayList<>(changes.keySet());
        changedIds.sort(Id.ID_COMPARATOR);

        explicitNetTokenUnitOrOwnershipChanges = new ArrayList<>();
        for (final var id : changedIds) {
            final var tokenId = id.asGrpcToken();
            final List<NftTransfer> transfers = new ArrayList<>();
            for (final var change : changes.get(id)) {
                transfers.add(
                        NftTransfer.newBuilder()
                                .setSenderAccountID(change.getPreviousOwner().asGrpcAccount())
                                .setReceiverAccountID(change.getNewOwner().asGrpcAccount())
                                .setSerialNumber(change.getSerialNumber())
                                .build());
            }
            explicitNetTokenUnitOrOwnershipChanges.add(
                    TokenTransferList.newBuilder()
                            .setToken(tokenId)
                            .addAllNftTransfers(transfers)
                            .build());
        }
    }

    /**
     * Tracks the summarized balance changes (if any) contained in the given token relationships.
     *
     * @param changedTokenRels the changed token relationships
     */
    public void trackTokenBalanceChanges(final List<TokenRelationship> changedTokenRels) {
        final Map<Id, TokenTransferList.Builder> changesById = new HashMap<>();

        for (final var tokenRel : changedTokenRels) {
            if (!tokenRel.hasChangesForRecord()) {
                continue;
            }

            final var tokenId = tokenRel.getToken().getId();
            final var accountId = tokenRel.getAccount().getId();
            final var scopedChanges =
                    changesById.computeIfAbsent(
                            tokenId,
                            ignore ->
                                    TokenTransferList.newBuilder().setToken(tokenId.asGrpcToken()));
            scopedChanges.addTransfers(
                    AccountAmount.newBuilder()
                            .setAccountID(accountId.asGrpcAccount())
                            .setAmount(tokenRel.getBalanceChange()));
        }

        if (!changesById.isEmpty()) {
            explicitNetTokenUnitOrOwnershipChanges = new ArrayList<>();
            final List<Id> tokenIds = new ArrayList<>(changesById.keySet());
            tokenIds.sort(Id.ID_COMPARATOR);
            tokenIds.forEach(
                    id -> explicitNetTokenUnitOrOwnershipChanges.add(changesById.get(id).build()));
        }
    }

    /**
     * Indicates whether there any new token id was tracked this transaction.
     *
     * @return if any new token id was tracked
     */
    public boolean hasTrackedNewTokenId() {
        return newTokenId != null;
    }

    /**
     * Returns the new token id that occurred during the transaction.
     *
     * @return the new token id
     */
    public TokenID getTrackedNewTokenId() {
        return newTokenId;
    }

    public boolean hasTrackedAutoCreation() {
        return newAccountId != null;
    }

    public boolean hasTrackedHollowAccountUpdate() {
        return hollowAccountId != null;
    }

    public ByteString getNewEntityAlias() {
        return newEntityAlias;
    }

    public AccountID getTrackedAutoCreatedAccountId() {
        return newAccountId;
    }

    public AccountID getTrackedHollowAccountId() {
        return hollowAccountId;
    }

    public ContractID getTrackedNewContractId() {
        return newContractId;
    }

    public boolean hasTrackedContractCreation() {
        return newContractId != null;
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
     * Returns the token supply change that occurred during the transaction.
     *
     * @return the token supply change
     */
    public long getTrackedTokenSupply() {
        return newSupply;
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
     * Returns the list of NFT serial numbers minted during the transaction; these will be in
     * consecutive ascending order.
     *
     * @return the tracked NFT mints
     */
    public List<Long> getTrackedNftMints() {
        return nftMints;
    }

    /**
     * Tracks an account/token association automatically created (either by a {@code TokenCreate} or
     * a {@code CryptoTransfer}).
     *
     * @param token the token involved in the auto-association
     * @param account the account involved in the auto-association
     */
    public void trackAutoAssociation(final TokenID token, final AccountID account) {
        final var association =
                new FcTokenAssociation(token.getTokenNum(), account.getAccountNum());
        autoAssociations.add(association);
    }

    /**
     * Tracks the reward payment to the account.
     *
     * @param accountNum Account number of the account receiving the reward.
     * @param amount Reward amount.
     */
    public void trackRewardPayment(final long accountNum, final long amount) {
        if (amount != 0) {
            numRewardedAccounts =
                    includeOrderedFungibleChange(
                            rewardedAccounts,
                            rewardAmounts,
                            numRewardedAccounts,
                            accountNum,
                            amount);
        }
    }

    /**
     * Returns the list of automatically created account/token associations, in the order they were
     * created during the transaction.
     *
     * @return the created auto-associations
     */
    public List<FcTokenAssociation> getTrackedAutoAssociations() {
        return autoAssociations.isEmpty()
                ? Collections.emptyList()
                : new ArrayList<>(autoAssociations);
    }

    /**
     * Tracks an incremental ℏ balance change for the given account. It is important to note that
     * each change is <b>incremental</b>; that is, two consecutive calls {@code
     * hbarChange(0.0.12345, +1)} and {@code hbarChange(0.0.12345, +2)} are equivalent to a single
     * {@code hbarChange(0.0.12345, +3)} call.
     *
     * @param account the changed account
     * @param amount the incremental ℏ change to track
     */
    public void trackHbarChange(final long account, final long amount) {
        netHbarChange += amount;
        numHbarChangesSoFar =
                includeOrderedFungibleChange(
                        changedAccounts, balanceChanges, numHbarChangesSoFar, account, amount);
    }

    /**
     * Tracks an incremental balance change for the given account in units of the given token. It is
     * important to note that each change is <b>incremental</b>; that is, two consecutive calls
     * {@code tokenUnitsChange(0.0.666, 0.0.12345, +1)} and {@code tokenUnitsChange(0.0.666,
     * 0.0.12345, +2)} are equivalent to a single {@code tokenUnitsChange(0.0.666, 0.0.12345, +3)}
     * call.
     *
     * @param token the denomination of the balance change
     * @param account the changed account
     * @param amount the incremental unit change to track
     */
    public void trackTokenUnitsChange(
            final TokenID token, final AccountID account, final long amount) {
        tokensTouched[numTokenChangesSoFar++] = token;
        final var unitChanges =
                netTokenChanges.computeIfAbsent(token, ignore -> TransferList.newBuilder());
        updateFungibleChanges(account, amount, unitChanges);
    }

    /**
     * Tracks ownership of the given NFT changing from the given sender to the given receiver. This
     * tracking does <b>not</b> perform a "transitive closure" over ownership changes; that is, if
     * say NFT {@code 0.0.666.1} changes ownership twice in the same transaction, once from {@code
     * 0.0.12345} to {@code 0.0.23456}, and again from {@code 0.0.23456} to {@code 0.0.34567}, then
     * <b>both</b> these ownership changes will be recorded in the list returned by {@link
     * SideEffectsTracker#getNetTrackedTokenUnitAndOwnershipChanges()}.
     *
     * @param nftId the NFT changing hands
     * @param from the sender of the NFT
     * @param to the receiver of the NFT
     */
    public void trackNftOwnerChange(final NftId nftId, final AccountID from, AccountID to) {
        final var token = nftId.tokenId();
        tokensTouched[numTokenChangesSoFar++] = token;
        var xfers =
                nftOwnerChanges.computeIfAbsent(token, ignore -> TokenTransferList.newBuilder());
        xfers.addNftTransfers(nftTransferBuilderWith(from, to, nftId.serialNo()));
    }

    /**
     * Returns the list of net ℏ balance changes including all incremental side effects tracked
     * since the last call to {@link SideEffectsTracker#reset()}. The returned adjustments are
     * sorted in ascending order of account numbers
     *
     * @return the ordered net balance changes
     */
    public CurrencyAdjustments getNetTrackedHbarChanges() {
        numHbarChangesSoFar =
                purgeZeroChanges(changedAccounts, balanceChanges, numHbarChangesSoFar);

        // copy the range of elements that are modified from balance changes and account numbers
        final long[] changedBalances = Arrays.copyOfRange(balanceChanges, 0, numHbarChangesSoFar);
        final long[] changedAccountNums =
                Arrays.copyOfRange(changedAccounts, 0, numHbarChangesSoFar);
        return CurrencyAdjustments.fromChanges(changedBalances, changedAccountNums);
    }

    public long getNetHbarChange() {
        return netHbarChange;
    }

    public CurrencyAdjustments getStakingRewardsPaid() {
        final long[] rewards = Arrays.copyOfRange(rewardAmounts, 0, numRewardedAccounts);
        final long[] accounts = Arrays.copyOfRange(rewardedAccounts, 0, numRewardedAccounts);
        return CurrencyAdjustments.fromChanges(rewards, accounts);
    }

    /**
     * Returns the list-of-lists of net token changes (in unit balances for fungible token types,
     * NFT ownership changes for non-fungible), including all incremental side effects since the
     * last call to {@link SideEffectsTracker#reset()}. The outer list is sorted in ascending order
     * by the {@link com.hedera.services.ledger.HederaLedger#TOKEN_ID_COMPARATOR}. Inner lists that
     * represent changes in fungible unit balances are sorted in ascending order by the {@link
     * com.hedera.services.ledger.HederaLedger#ACCOUNT_ID_COMPARATOR}; while inner lists that
     * represent NFT ownership changes are in the order the NFTs were exchanged in the transaction.
     *
     * @return the ordered list of ordered balance and NFT ownership changes
     */
    public List<TokenTransferList> getNetTrackedTokenUnitAndOwnershipChanges() {
        if (explicitNetTokenUnitOrOwnershipChanges != null) {
            return explicitNetTokenUnitOrOwnershipChanges;
        }

        if (numTokenChangesSoFar == 0) {
            return Collections.emptyList();
        }
        final List<TokenTransferList> all = new ArrayList<>();
        Arrays.sort(tokensTouched, 0, numTokenChangesSoFar, TOKEN_ID_COMPARATOR);
        for (int i = 0; i < numTokenChangesSoFar; i++) {
            var token = tokensTouched[i];
            if (i == 0 || !token.equals(tokensTouched[i - 1])) {
                final var uniqueTransfersHere = nftOwnerChanges.get(token);
                if (uniqueTransfersHere != null) {
                    all.add(
                            TokenTransferList.newBuilder()
                                    .setToken(token)
                                    .addAllNftTransfers(uniqueTransfersHere.getNftTransfersList())
                                    .build());
                } else {
                    final var fungibleTransfersHere = netTokenChanges.get(token);
                    if (fungibleTransfersHere != null) {
                        purgeZeroAdjustments(fungibleTransfersHere);
                        all.add(
                                TokenTransferList.newBuilder()
                                        .setToken(token)
                                        .addAllTransfers(
                                                fungibleTransfersHere.getAccountAmountsList())
                                        .build());
                    }
                }
            }
        }
        return all;
    }

    public void trackRandomBytes(final byte[] bytes) {
        this.pseudorandomBytes = bytes;
    }

    public void trackRandomNumber(final int pseudoRandomNumber) {
        this.pseudorandomNumber = pseudoRandomNumber;
    }

    public byte[] getPseudorandomBytes() {
        return pseudorandomBytes;
    }

    public int getPseudorandomNumber() {
        return pseudorandomNumber;
    }

    public boolean hasTrackedRandomData() {
        return pseudorandomNumber >= 0
                || (pseudorandomBytes != null && pseudorandomBytes.length > 0);
    }

    /** Clears all side effects tracked since the last call to this method. */
    public void reset() {
        resetTrackedTokenChanges();
        numHbarChangesSoFar = 0;
        numRewardedAccounts = 0;
        netHbarChange = 0;
        newAccountId = null;
        newContractId = null;
        newEntityAlias = ByteString.EMPTY;
        pseudorandomNumber = MISSING_NUMBER;
        pseudorandomBytes = null;
        hollowAccountId = null;
    }

    /**
     * Clears all token-related side effects tracked since the last call to this method. These
     * include:
     *
     * <ul>
     *   <li>Changes to balances of fungible token units.
     *   <li>Changes to NFT owners.
     *   <li>Automatically created token associations.
     * </ul>
     */
    public void resetTrackedTokenChanges() {
        for (int i = 0; i < numTokenChangesSoFar; i++) {
            final var fungibleBuilder = netTokenChanges.get(tokensTouched[i]);
            if (fungibleBuilder != null) {
                fungibleBuilder.clearAccountAmounts();
            } else {
                nftOwnerChanges.get(tokensTouched[i]).clearNftTransfers();
            }
        }
        numTokenChangesSoFar = 0;

        newSupply = INAPPLICABLE_NEW_SUPPLY;
        newTokenId = null;
        nftMints.clear();
        autoAssociations.clear();
        explicitNetTokenUnitOrOwnershipChanges = null;
    }

    /* --- Internal helpers --- */
    private void updateFungibleChanges(
            final AccountID account, final long amount, final TransferList.Builder builder) {
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
            final AccountID senderId, final AccountID receiverId, final long serialNumber) {
        return NftTransfer.newBuilder()
                .setSenderAccountID(senderId)
                .setReceiverAccountID(receiverId)
                .setSerialNumber(serialNumber);
    }

    private AccountAmount.Builder aaBuilderWith(final AccountID account, final long amount) {
        return AccountAmount.newBuilder().setAccountID(account).setAmount(amount);
    }

    /**
     * Incorporates a balance change for a target account into two parallel {@code long[]} arrays
     * that represent all the cumulative balance changes so far to all touched accounts, <b>in
     * ascending order of account number</b>.
     *
     * <p>Returns the new number of touched accounts after incorporating this change.
     *
     * <p><b>IMPORTANT:</b> This method assumes the parallel arrays are large enough to include a
     * new balance change without resizing. (It takes care of shifting larger-numbered accounts to
     * the right if needed.)
     *
     * @param accountNums an array of account numbers whose balances have changed so far
     * @param balanceChanges the parallel array of balance changes
     * @param touchedSoFar how many slots in the accountNums array were actually touched so far
     * @param targetNum the account number for the new balance change
     * @param newChange an additional change to incorporate into the target account's balance
     * @return how many slots in the accountNums array are now actually touched
     */
    @VisibleForTesting
    static int includeOrderedFungibleChange(
            final long[] accountNums,
            final long[] balanceChanges,
            final int touchedSoFar,
            final long targetNum,
            final long newChange) {
        var i = touchedSoFar - 1;
        // Start from the rightmost touched account, skip accounts larger than our target
        while (i >= 0 && accountNums[i] > targetNum) {
            i--;
        }
        if (i == -1 || accountNums[i] != targetNum) {
            // The target num wasn't already present, so we need to shift all larger accounts one to
            // the right
            for (int j = touchedSoFar - 1; j > i; j--) {
                accountNums[j + 1] = accountNums[j];
                balanceChanges[j + 1] = balanceChanges[j];
            }
            // And now insert our new change
            i++;
            accountNums[i] = targetNum;
            balanceChanges[i] = newChange;
            return touchedSoFar + 1;
        } else {
            // The target num was already present, so just update the balance
            balanceChanges[i] += newChange;
            return touchedSoFar;
        }
    }

    /**
     * Removes any zero-impact balance changes from two parallel {@code long[]} arrays that
     * represent all the cumulative balance changes so far to all touched accounts; returns the new
     * number of touched accounts after zero balances have been purged.
     *
     * @param accountNums an array of account numbers whose balances have changed so far
     * @param balanceChanges the parallel array of balance changes
     * @param touchedSoFar how many slots in the accountNums array were actually touched so far
     * @return how many slots in the accountNums array are now actually touched
     */
    @VisibleForTesting
    static int purgeZeroChanges(
            final long[] accountNums, final long[] balanceChanges, final int touchedSoFar) {
        var zerosSkippedSoFar = 0;
        var retracedI = 0;
        for (int i = 0; i < touchedSoFar; i++, retracedI++) {
            if (balanceChanges[i] == 0) {
                zerosSkippedSoFar++;
                retracedI--;
            } else if (zerosSkippedSoFar > 0) {
                // shift the elements in array to left replacing zero changes
                accountNums[retracedI] = accountNums[i];
                balanceChanges[retracedI] = balanceChanges[i];
            }
        }
        return touchedSoFar - zerosSkippedSoFar;
    }

    @VisibleForTesting
    int getNumHbarChangesSoFar() {
        return numHbarChangesSoFar;
    }

    @VisibleForTesting
    int getNumRewardedAccounts() {
        return numRewardedAccounts;
    }

    @VisibleForTesting
    List<FcTokenAssociation> getInternalAutoAssociations() {
        return autoAssociations;
    }
}
