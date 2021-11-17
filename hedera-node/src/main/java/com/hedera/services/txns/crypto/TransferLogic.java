package com.hedera.services.txns.crypto;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.BalanceChange;
import com.hedera.services.ledger.MerkleAccountScopedCheck;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.ledger.properties.NftProperty;
import com.hedera.services.ledger.properties.TokenProperty;
import com.hedera.services.ledger.properties.TokenRelProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleAccountTokens;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleTokenRelStatus;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.FcTokenAssociation;
import com.hedera.services.store.models.NftId;
import com.hedera.services.store.tokens.views.UniqTokenViewsManager;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransferList;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;

import static com.hedera.services.ledger.accounts.BackingTokenRels.asTokenRel;
import static com.hedera.services.ledger.properties.AccountProperty.ALREADY_USED_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.EXPIRY;
import static com.hedera.services.ledger.properties.AccountProperty.IS_DELETED;
import static com.hedera.services.ledger.properties.AccountProperty.IS_SMART_CONTRACT;
import static com.hedera.services.ledger.properties.AccountProperty.MAX_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.services.ledger.properties.AccountProperty.NUM_NFTS_OWNED;
import static com.hedera.services.ledger.properties.AccountProperty.TOKENS;
import static com.hedera.services.ledger.properties.NftProperty.OWNER;
import static com.hedera.services.ledger.properties.TokenRelProperty.IS_FROZEN;
import static com.hedera.services.ledger.properties.TokenRelProperty.IS_KYC_GRANTED;
import static com.hedera.services.ledger.properties.TokenRelProperty.TOKEN_BALANCE;
import static com.hedera.services.state.enums.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hedera.services.state.submerkle.EntityId.fromGrpcAccountId;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_TOKEN_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_NFT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.NO_REMAINING_AUTOMATIC_ASSOCIATIONS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_IS_PAUSED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;

public class TransferLogic {
    private final MerkleAccountScopedCheck scopedCheck;
    private final TransferList.Builder netTransfers = TransferList.newBuilder();
    private final GlobalDynamicProperties dynamicProperties;
    private final OptionValidator validator;
    private UniqTokenViewsManager tokenViewsManager = null;

    private final TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;
    private final TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokenLedger;
    private final TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nftsLedger;
    private final TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger;
    private final SideEffectsTracker sideEffectsTracker;

    // TODO: bundle in a utils class
    TokenID MISSING_TOKEN = TokenID.getDefaultInstance();

    // TODO: move to SideEffectsTracker
    private final List<FcTokenAssociation> newTokenAssociations = new ArrayList<>();

    public TransferLogic(MerkleAccountScopedCheck scopedCheck, GlobalDynamicProperties dynamicProperties, OptionValidator validator, TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger, TransactionalLedger<TokenID, TokenProperty, MerkleToken> tokenLedger, TransactionalLedger<NftId, NftProperty, MerkleUniqueToken> nftsLedger, TransactionalLedger<Pair<AccountID, TokenID>, TokenRelProperty, MerkleTokenRelStatus> tokenRelsLedger, SideEffectsTracker sideEffectsTracker) {
        this.scopedCheck = scopedCheck;
        this.dynamicProperties = dynamicProperties;
        this.validator = validator;
        this.accountsLedger = accountsLedger;
        this.tokenLedger = tokenLedger;
        this.nftsLedger = nftsLedger;
        this.tokenRelsLedger = tokenRelsLedger;
        this.sideEffectsTracker = sideEffectsTracker;
    }

    public void setTokenViewsManager(UniqTokenViewsManager tokenViewsManager) {
        this.tokenViewsManager = tokenViewsManager;
    }

    public void cryptoTransfer(List<BalanceChange> changes) {
        doZeroSum(changes);
    }

    private void doZeroSum(List<BalanceChange> pendingChanges) {
        var validity = OK;
        for (var change : pendingChanges) {
            if (change.isForHbar()) {
                validity = accountsLedger.validate(
                        change.accountId(),
                        scopedCheck.setBalanceChange(change));
            }
            if (validity != OK) {
                return;
            }
        }

        // Set HBAR balance changes and commit them on the upper level
        for (var change : pendingChanges) {
            if (change.isForHbar()) {
                accountsLedger.set(change.accountId(), BALANCE, change.getNewBalance());
                // TODO: move updateXfers to SideEffectsTracker
            }
        }
    }

    ResponseCodeEnum tryTokenChange(BalanceChange change) {
        var validity = OK;
        var tokenId = resolve(change.tokenId());
        if (tokenId == MISSING_TOKEN) {
            validity = INVALID_TOKEN_ID;
        }
        if (validity == OK) {
            if (change.isForNft()) {
                validity = changeOwner(change.nftId(), change.accountId(), change.counterPartyAccountId());
            } else {
                validity = adjustBalance(change.accountId(), tokenId, change.units());
                if (validity == INSUFFICIENT_TOKEN_BALANCE) {
                    validity = change.codeForInsufficientBalance();
                }
            }
        }
        return validity;
    }

    private ResponseCodeEnum adjustBalance(final AccountID aId, final TokenID tId, final long adjustment) {
        return sanityCheckedFungibleCommon(aId, tId, token -> tryAdjustment(aId, tId, adjustment));
    }

    private ResponseCodeEnum tryAdjustment(final AccountID aId, final TokenID tId, final long adjustment) {
        final var freezeAndKycValidity = checkRelFrozenAndKycProps(aId, tId);
        if (!freezeAndKycValidity.equals(OK)) {
            return freezeAndKycValidity;
        }

        final var relationship = asTokenRel(aId, tId);
        final var balance = (long) tokenRelsLedger.get(relationship, TOKEN_BALANCE);
        final var newBalance = balance + adjustment;
        if (newBalance < 0) {
            return INSUFFICIENT_TOKEN_BALANCE;
        }
        tokenRelsLedger.set(relationship, TOKEN_BALANCE, newBalance);

        // TODO: move to SideEffectsTracker
        //        hederaLedger.updateTokenXfers(tId, aId, adjustment);
        return OK;
    }

    private ResponseCodeEnum sanityCheckedFungibleCommon(
            final AccountID aId,
            final TokenID tId,
            final Function<MerkleToken, ResponseCodeEnum> action
    ) {
        return sanityChecked(true, aId, null, tId, action);
    }

    private TokenID resolve(TokenID tokenID) {
        // TODO: check if creationPending and pendingId is still needed
        return this.tokenLedger.exists(tokenID) ? tokenID : MISSING_TOKEN;
    }

    private ResponseCodeEnum changeOwner(final NftId nftId, final AccountID from, final AccountID to) {
        final var tId = nftId.tokenId();
        return sanityChecked(false, from, to, tId, token -> {
            if (!nftsLedger.exists(nftId)) {
                return INVALID_NFT_ID;
            }

            final var fromFreezeAndKycValidity = checkRelFrozenAndKycProps(from, tId);
            if (fromFreezeAndKycValidity != OK) {
                return fromFreezeAndKycValidity;
            }
            final var toFreezeAndKycValidity = checkRelFrozenAndKycProps(to, tId);
            if (toFreezeAndKycValidity != OK) {
                return toFreezeAndKycValidity;
            }

            var owner = (EntityId) nftsLedger.get(nftId, OWNER);
            if (owner.equals(fromGrpcAccountId(AccountID.getDefaultInstance()))) {
                final var tid = nftId.tokenId();
                owner = (EntityId) this.tokenLedger.get(tId, TokenProperty.TREASURY);
            }
            if (!owner.matches(from)) {
                return SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
            }

            submitPotentialChanges(nftId, from, to, tId, owner);
            return OK;
        });
    }

    private void submitPotentialChanges(
            final NftId nftId,
            final AccountID from,
            final AccountID to,
            final TokenID tId,
            final EntityId owner
    ) {
        final var nftType = nftId.tokenId();
        final var fromRel = asTokenRel(from, nftType);
        final var toRel = asTokenRel(to, nftType);

        final var fromNftsOwned = (long) accountsLedger.get(from, NUM_NFTS_OWNED);
        final var fromThisNftsOwned = (long) tokenRelsLedger.get(fromRel, TOKEN_BALANCE);
        final var toNftsOwned = (long) accountsLedger.get(to, NUM_NFTS_OWNED);
        final var toThisNftsOwned = (long) tokenRelsLedger.get(asTokenRel(to, nftType), TOKEN_BALANCE);

        // TODO: move to SideEffectsTracker
//        final var isTreasuryReturn = isTreasuryForToken(to, tId);
//        if (isTreasuryReturn) {
//            nftsLedger.set(nftId, OWNER, EntityId.MISSING_ENTITY_ID);
//        } else {
//            nftsLedger.set(nftId, OWNER, EntityId.fromGrpcAccountId(to));
//        }

        /* Note correctness here depends on rejecting self-transfers */
        accountsLedger.set(from, NUM_NFTS_OWNED, fromNftsOwned - 1);
        accountsLedger.set(to, NUM_NFTS_OWNED, toNftsOwned + 1);
        tokenRelsLedger.set(fromRel, TOKEN_BALANCE, fromThisNftsOwned - 1);
        tokenRelsLedger.set(toRel, TOKEN_BALANCE, toThisNftsOwned + 1);

        // TODO: move to SideEffectsTracker
//        final var merkleNftId = EntityNumPair.fromLongs(nftId.tokenId().getTokenNum(), nftId.serialNo());
//        final var receiver = fromGrpcAccountId(to);
//        if (isTreasuryReturn) {
//            uniqTokenViewsManager.treasuryReturnNotice(merkleNftId, owner, receiver);
//        } else {
//            final var isTreasuryExit = isTreasuryForToken(from, tId);
//            if (isTreasuryExit) {
//                uniqTokenViewsManager.treasuryExitNotice(merkleNftId, owner, receiver);
//            } else {
//                uniqTokenViewsManager.exchangeNotice(merkleNftId, owner, receiver);
//            }
//        }
//        hederaLedger.updateOwnershipChanges(nftId, from, to);
    }

    private ResponseCodeEnum checkRelFrozenAndKycProps(final AccountID aId, final TokenID tId) {
        final var relationship = asTokenRel(aId, tId);
        if ((boolean) tokenRelsLedger.get(relationship, IS_FROZEN)) {
            return ACCOUNT_FROZEN_FOR_TOKEN;
        }
        if (!(boolean) tokenRelsLedger.get(relationship, IS_KYC_GRANTED)) {
            return ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN;
        }
        return OK;
    }

    private ResponseCodeEnum sanityChecked(
            final boolean onlyFungibleCommon,
            final AccountID aId,
            final AccountID aCounterPartyId,
            final TokenID tId,
            final Function<MerkleToken, ResponseCodeEnum> action
    ) {
        var validity = checkAccountUsability(aId);
        if (validity != OK) {
            return validity;
        }
        if (aCounterPartyId != null) {
            validity = checkAccountUsability(aCounterPartyId);
            if (validity != OK) {
                return validity;
            }
        }

        validity = this.tokenLedger.exists(tId) ? OK : INVALID_TOKEN_ID;
        if (validity != OK) {
            return validity;
        }

        final var token = tokenLedger.getFinalized(tId);
        if (token.isDeleted()) {
            return TOKEN_WAS_DELETED;
        }
        if (token.isPaused()) {
            return TOKEN_IS_PAUSED;
        }
        if (onlyFungibleCommon && token.tokenType() == NON_FUNGIBLE_UNIQUE) {
            return ACCOUNT_AMOUNT_TRANSFERS_ONLY_ALLOWED_FOR_FUNGIBLE_COMMON;
        }

        var key = asTokenRel(aId, tId);
        /*
         * Instead of returning  TOKEN_NOT_ASSOCIATED_TO_ACCOUNT when a token is not associated,
         * we check if the account has any maxAutoAssociations set up, if they do check if we reached the limit and
         * auto associate. If not return EXISTING_AUTOMATIC_ASSOCIATIONS_EXCEED_GIVEN_LIMIT
         */
        if (!tokenRelsLedger.exists(key)) {
            validity = validateAndAutoAssociate(aId, tId);
            if (validity != OK) {
                return validity;
            }
        }
        if (aCounterPartyId != null) {
            key = asTokenRel(aCounterPartyId, tId);
            if (!tokenRelsLedger.exists(key)) {
                validity = validateAndAutoAssociate(aCounterPartyId, tId);
                if (validity != OK) {
                    return validity;
                }
            }
        }

        return action.apply(token);
    }

    private ResponseCodeEnum validateAndAutoAssociate(AccountID aId, TokenID tId) {
        if ((int) accountsLedger.get(aId, MAX_AUTOMATIC_ASSOCIATIONS) > 0) {
            return associate(aId, List.of(tId), true);
        }
        return TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
    }

    private ResponseCodeEnum fullySanityChecked(
            final boolean strictTokenCheck,
            final AccountID aId,
            final List<TokenID> tokens,
            final BiFunction<AccountID, List<TokenID>, ResponseCodeEnum> action
    ) {
        final var validity = checkAccountUsability(aId);
        if (validity != OK) {
            return validity;
        }
        if (strictTokenCheck) {
            for (var tID : tokens) {
                final var id = resolve(tID);
                if (id == MISSING_TOKEN) {
                    return INVALID_TOKEN_ID;
                }
                final var token = tokenLedger.getFinalized(id);
                if (token.isDeleted()) {
                    return TOKEN_WAS_DELETED;
                }
            }
        }
        return action.apply(aId, tokens);
    }

    private ResponseCodeEnum associate(AccountID aId, List<TokenID> tokens, boolean automaticAssociation) {
        return fullySanityChecked(true, aId, tokens, (account, tokenIds) -> {
            // TODO: should we use it the way below or use backing collection instead
            final var accountTokens = (MerkleAccountTokens) accountsLedger.get(aId, TOKENS);
            for (var id : tokenIds) {
                if (accountTokens.includes(id)) {
                    return TOKEN_ALREADY_ASSOCIATED_TO_ACCOUNT;
                }
            }
            var validity = OK;
            if ((accountTokens.numAssociations() + tokenIds.size()) > dynamicProperties.maxTokensPerAccount()) {
                validity = TOKENS_PER_ACCOUNT_LIMIT_EXCEEDED;
            } else {
                var maxAutomaticAssociations = (int) accountsLedger.get(aId, MAX_AUTOMATIC_ASSOCIATIONS);
                var alreadyUsedAutomaticAssociations = (int) accountsLedger.get(aId, ALREADY_USED_AUTOMATIC_ASSOCIATIONS);

                if (automaticAssociation && alreadyUsedAutomaticAssociations >= maxAutomaticAssociations) {
                    validity = NO_REMAINING_AUTOMATIC_ASSOCIATIONS;
                }

                if (validity == OK) {
                    accountTokens.associateAll(new HashSet<>(tokenIds));
                    for (var id : tokenIds) {
                        final var relationship = asTokenRel(aId, id);
                        tokenRelsLedger.create(relationship);
                        final var token = tokenLedger.getFinalized(id);
                        tokenRelsLedger.set(
                                relationship,
                                TokenRelProperty.IS_FROZEN,
                                token.hasFreezeKey() && token.accountsAreFrozenByDefault());
                        tokenRelsLedger.set(
                                relationship,
                                TokenRelProperty.IS_KYC_GRANTED,
                                !token.hasKycKey());
                        tokenRelsLedger.set(
                                relationship,
                                TokenRelProperty.IS_AUTOMATIC_ASSOCIATION,
                                automaticAssociation);

                        // TODO: move to SideEffectsTracker
//                        hederaLedger.addNewAssociationToList(
//                                new FcTokenAssociation(id.getTokenNum(), aId.getAccountNum()));
//                        if (automaticAssociation) {
//                            hederaLedger.setAlreadyUsedAutomaticAssociations(aId, alreadyUsedAutomaticAssociations + 1);
//                        }
                    }
                }
            }
            // TODO: move to SideEffectsTracker
//            hederaLedger.setAssociatedTokens(aId, accountTokens);
            return validity;
        });
    }

    private ResponseCodeEnum checkAccountUsability(AccountID aId) {
        if (!accountsLedger.exists(aId)) {
            return INVALID_ACCOUNT_ID;
        } else if (isDeleted(aId)) {
            return ACCOUNT_DELETED;
        } else if (isDetached(aId)) {
            return ACCOUNT_EXPIRED_AND_PENDING_REMOVAL;
        } else {
            return OK;
        }
    }

    public boolean isDeleted(AccountID id) {
        return (boolean) accountsLedger.get(id, IS_DELETED);
    }

    public boolean isDetached(AccountID id) {
        return dynamicProperties.autoRenewEnabled()
                && !(boolean) accountsLedger.get(id, IS_SMART_CONTRACT)
                && (long) accountsLedger.get(id, BALANCE) == 0L
                && !validator.isAfterConsensusSecond((long) accountsLedger.get(id, EXPIRY));
    }
}
