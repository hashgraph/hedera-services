/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer;

import static com.hedera.node.app.spi.HapiUtils.ACCOUNT_ID_COMPARATOR;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class ClassicTransfersDecoder {
    @Inject
    public ClassicTransfersDecoder() {
        // Dagger2
    }

    enum IsApproval {
        TRUE,
        FALSE
    }

    @FunctionalInterface
    interface FungibleAdjustmentConverter {
        TokenTransferList convert(
                @NonNull Address token, @NonNull Tuple[] adjustments, @NonNull AddressIdConverter addressIdConverter);
    }

    @FunctionalInterface
    interface OwnershipChangeConverter {
        TokenTransferList convert(
                @NonNull Address token,
                @NonNull Tuple[] ownershipChanges,
                @NonNull AddressIdConverter addressIdConverter);
    }

    /**
     * Decodes a call to {@link ClassicTransfersTranslator#CRYPTO_TRANSFER} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeCryptoTransfer(
            @NonNull final byte[] encoded, @NonNull final AddressIdConverter addressIdConverter) {
        final var call = ClassicTransfersTranslator.CRYPTO_TRANSFER.decodeCall(encoded);
        return bodyOf(tokenTransfers(convertTokenTransfers(
                call.get(0), this::convertingAdjustments, this::convertingOwnershipChanges, addressIdConverter)));
    }

    /**
     * Decodes a call to {@link ClassicTransfersTranslator#CRYPTO_TRANSFER_V2} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeCryptoTransferV2(
            @NonNull final byte[] encoded, @NonNull final AddressIdConverter addressIdConverter) {
        final var call = ClassicTransfersTranslator.CRYPTO_TRANSFER_V2.decodeCall(encoded);
        return bodyOf(tokenTransfers(convertTokenTransfers(
                        call.get(1),
                        this::convertingMaybeApprovedAdjustments,
                        this::convertingMaybeApprovedOwnershipChanges,
                        addressIdConverter))
                .transfers(convertingMaybeApprovedAdjustments(((Tuple) call.get(0)).get(0), addressIdConverter)));
    }

    /**
     * Decodes a call to {@link ClassicTransfersTranslator#TRANSFER_TOKENS} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeTransferTokens(
            @NonNull final byte[] encoded, @NonNull final AddressIdConverter addressIdConverter) {
        final var call = ClassicTransfersTranslator.TRANSFER_TOKENS.decodeCall(encoded);
        return bodyOf(tokenTransfers(convertingAdjustments(call.get(0), call.get(1), call.get(2), addressIdConverter)));
    }

    /**
     * Decodes a call to {@link ClassicTransfersTranslator#TRANSFER_TOKEN} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeTransferToken(
            @NonNull final byte[] encoded, @NonNull final AddressIdConverter addressIdConverter) {
        final var call = ClassicTransfersTranslator.TRANSFER_TOKEN.decodeCall(encoded);
        return bodyOf(tokenTransfers(sendingUnitsFromTo(
                ConversionUtils.asTokenId(call.get(0)),
                addressIdConverter.convert(call.get(1)),
                addressIdConverter.convertCredit(call.get(2)),
                call.get(3),
                IsApproval.FALSE)));
    }

    /**
     * Decodes a call to {@link ClassicTransfersTranslator#TRANSFER_NFTS} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeTransferNfts(
            @NonNull final byte[] encoded, @NonNull final AddressIdConverter addressIdConverter) {
        final var call = ClassicTransfersTranslator.TRANSFER_NFTS.decodeCall(encoded);
        final Address[] from = call.get(1);
        final Address[] to = call.get(2);
        final long[] serialNo = call.get(3);
        if (from.length != to.length || from.length != serialNo.length) {
            throw new IllegalArgumentException("Mismatched argument arrays (# from=" + from.length + ", # to="
                    + to.length + ", # serialNo=" + serialNo.length + ")");
        }
        final var ownershipChanges = new NftTransfer[from.length];
        for (int i = 0; i < from.length; i++) {
            ownershipChanges[i] = nftTransfer(
                    addressIdConverter.convert(from[i]),
                    addressIdConverter.convertCredit(to[i]),
                    serialNo[i],
                    IsApproval.FALSE);
        }
        return bodyOf(tokenTransfers(changingOwners(ConversionUtils.asTokenId(call.get(0)), ownershipChanges)));
    }

    /**
     * Decodes a call to {@link ClassicTransfersTranslator#TRANSFER_NFT} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeTransferNft(
            @NonNull final byte[] encoded, @NonNull final AddressIdConverter addressIdConverter) {
        final var call = ClassicTransfersTranslator.TRANSFER_NFT.decodeCall(encoded);
        return bodyOf(tokenTransfers(changingOwner(
                ConversionUtils.asTokenId(call.get(0)),
                addressIdConverter.convert(call.get(1)),
                addressIdConverter.convertCredit(call.get(2)),
                call.get(3),
                IsApproval.FALSE)));
    }

    /**
     * Decodes a call to {@link ClassicTransfersTranslator#TRANSFER_FROM} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeHrcTransferFrom(
            @NonNull final byte[] encoded, @NonNull final AddressIdConverter addressIdConverter) {
        final var call = ClassicTransfersTranslator.TRANSFER_FROM.decodeCall(encoded);
        return bodyOf(tokenTransfers(sendingUnitsFromTo(
                ConversionUtils.asTokenId(call.get(0)),
                addressIdConverter.convert(call.get(1)),
                addressIdConverter.convertCredit(call.get(2)),
                exactLongValueOrThrow(call.get(3)),
                IsApproval.TRUE)));
    }

    /**
     * Decodes a call to {@link ClassicTransfersTranslator#TRANSFER_NFT_FROM} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeHrcTransferNftFrom(
            @NonNull final byte[] encoded, @NonNull final AddressIdConverter addressIdConverter) {
        final var call = ClassicTransfersTranslator.TRANSFER_NFT_FROM.decodeCall(encoded);
        return bodyOf(tokenTransfers(changingOwner(
                ConversionUtils.asTokenId(call.get(0)),
                addressIdConverter.convert(call.get(1)),
                addressIdConverter.convertCredit(call.get(2)),
                exactLongValueOrThrow(call.get(3)),
                IsApproval.TRUE)));
    }

    public ResponseCodeEnum checkForFailureStatus(@NonNull HtsCallAttempt attempt) {
        if (Arrays.equals(attempt.selector(), ClassicTransfersTranslator.TRANSFER_TOKEN.selector())) {
            final var call = ClassicTransfersTranslator.TRANSFER_TOKEN.decodeCall(attempt.inputBytes());
            if ((long) call.get(3) < 0) {
                return ResponseCodeEnum.INVALID_TRANSACTION_BODY;
            }
        }
        return null;
    }

    private TokenTransferList[] convertTokenTransfers(
            @NonNull final Tuple[] transfersByToken,
            @NonNull final FungibleAdjustmentConverter fungibleAdjustmentConverter,
            @NonNull final OwnershipChangeConverter ownershipChangeConverter,
            @NonNull final AddressIdConverter addressIdConverter) {
        final TokenTransferList[] allImpliedTransfers = new TokenTransferList[transfersByToken.length];
        for (int i = 0; i < transfersByToken.length; i++) {
            final var transfers = transfersByToken[i];
            final Tuple[] unitAdjustments = transfers.get(1);
            if (unitAdjustments.length > 0) {
                allImpliedTransfers[i] =
                        fungibleAdjustmentConverter.convert(transfers.get(0), unitAdjustments, addressIdConverter);
            } else {
                allImpliedTransfers[i] =
                        ownershipChangeConverter.convert(transfers.get(0), transfers.get(2), addressIdConverter);
            }
        }
        return allImpliedTransfers;
    }

    private CryptoTransferTransactionBody.Builder tokenTransfers(@NonNull TokenTransferList... tokenTransferLists) {
        if (repeatsTokenId(tokenTransferLists)) {
            final Map<TokenID, TokenTransferList> consolidatedTokenTransfers = new LinkedHashMap<>();
            for (final var tokenTransferList : tokenTransferLists) {
                consolidatedTokenTransfers.merge(
                        tokenTransferList.tokenOrThrow(), tokenTransferList, this::mergeTokenTransferLists);
            }
            tokenTransferLists = consolidatedTokenTransfers.values().toArray(TokenTransferList[]::new);
        }
        return CryptoTransferTransactionBody.newBuilder().tokenTransfers(tokenTransferLists);
    }

    private TokenTransferList mergeTokenTransferLists(
            @NonNull final TokenTransferList from, @NonNull final TokenTransferList to) {
        return from.copyBuilder()
                .transfers(mergeTransfers(from.transfersOrElse(emptyList()), to.transfersOrElse(emptyList())))
                .nftTransfers(
                        mergeNftTransfers(from.nftTransfersOrElse(emptyList()), to.nftTransfersOrElse(emptyList())))
                .build();
    }

    private List<AccountAmount> mergeTransfers(
            @NonNull final List<AccountAmount> from, @NonNull final List<AccountAmount> to) {
        requireNonNull(from);
        requireNonNull(to);
        final Map<AccountID, AccountAmount> consolidated = new LinkedHashMap<>();
        consolidateInto(consolidated, from);
        consolidateInto(consolidated, to);
        return consolidated.values().stream().toList();
    }

    private void consolidateInto(
            @NonNull final Map<AccountID, AccountAmount> consolidated, @NonNull final List<AccountAmount> transfers) {
        for (final var transfer : transfers) {
            consolidated.merge(transfer.accountID(), transfer, this::mergeAdjusts);
        }
    }

    private AccountAmount mergeAdjusts(@NonNull final AccountAmount from, @NonNull final AccountAmount to) {
        return from.copyBuilder()
                .amount(from.amount() + to.amount())
                .isApproval(from.isApproval() || to.isApproval())
                .build();
    }

    private List<NftTransfer> mergeNftTransfers(
            @NonNull final List<NftTransfer> from, @NonNull final List<NftTransfer> to) {
        final Set<NftTransfer> present = new HashSet<>();
        final List<NftTransfer> consolidated = new ArrayList<>();
        consolidateInto(present, consolidated, from);
        consolidateInto(present, consolidated, to);
        return consolidated;
    }

    private void consolidateInto(
            @NonNull final Set<NftTransfer> present,
            @NonNull final List<NftTransfer> consolidated,
            @NonNull final List<NftTransfer> transfers) {
        for (final var transfer : transfers) {
            if (present.add(transfer)) {
                consolidated.add(transfer);
            }
        }
    }

    private boolean repeatsTokenId(@NonNull final TokenTransferList[] tokenTransferList) {
        return tokenTransferList.length > 1
                && Arrays.stream(tokenTransferList)
                                .map(TokenTransferList::token)
                                .collect(Collectors.toSet())
                                .size()
                        < tokenTransferList.length;
    }

    private TokenTransferList adjustingUnits(
            @NonNull final TokenID tokenId, @NonNull final AccountAmount... unitAdjustments) {
        return TokenTransferList.newBuilder()
                .token(tokenId)
                .transfers(unitAdjustments)
                .build();
    }

    private TokenTransferList sendingUnitsFromTo(
            @NonNull final TokenID tokenId,
            @NonNull final AccountID from,
            @NonNull final AccountID to,
            final long amount,
            final IsApproval isApproval) {
        final var accountAmounts = new ArrayList<AccountAmount>();
        accountAmounts.add(credit(to, amount));
        accountAmounts.add(debit(from, amount, isApproval));
        accountAmounts.sort(Comparator.comparing(AccountAmount::accountID, ACCOUNT_ID_COMPARATOR));

        return TokenTransferList.newBuilder()
                .token(tokenId)
                .transfers(accountAmounts)
                .build();
    }

    private TokenTransferList changingOwner(
            @NonNull final TokenID tokenId,
            @NonNull final AccountID from,
            @NonNull final AccountID to,
            final long serialNo,
            final IsApproval isApproval) {
        return TokenTransferList.newBuilder()
                .token(tokenId)
                .nftTransfers(nftTransfer(from, to, serialNo, isApproval))
                .build();
    }

    private TokenTransferList changingOwners(
            @NonNull final TokenID tokenId, @NonNull final NftTransfer... ownershipChanges) {
        return TokenTransferList.newBuilder()
                .token(tokenId)
                .nftTransfers(ownershipChanges)
                .build();
    }

    private AccountAmount debit(
            @NonNull final AccountID account, final long amount, @NonNull final IsApproval isApproval) {
        return adjust(account, -amount, isApproval);
    }

    private AccountAmount credit(@NonNull final AccountID account, final long amount) {
        return adjust(account, amount, IsApproval.FALSE);
    }

    private AccountAmount adjust(
            @NonNull final AccountID account, final long amount, @NonNull final IsApproval isApproval) {
        return AccountAmount.newBuilder()
                .accountID(account)
                .amount(amount)
                .isApproval(isApproval == IsApproval.TRUE)
                .build();
    }

    private NftTransfer nftTransfer(
            @NonNull final AccountID from,
            @NonNull final AccountID to,
            final long serialNo,
            final IsApproval isApproval) {
        return NftTransfer.newBuilder()
                .serialNumber(serialNo)
                .senderAccountID(from)
                .receiverAccountID(to)
                .isApproval(isApproval == IsApproval.TRUE)
                .build();
    }

    private TransactionBody bodyOf(@NonNull final CryptoTransferTransactionBody.Builder cryptoTransfer) {
        return TransactionBody.newBuilder().cryptoTransfer(cryptoTransfer).build();
    }

    private long exactLongValueOrThrow(@NonNull final BigInteger value) {
        return value.longValueExact();
    }

    private TokenTransferList convertingAdjustments(
            @NonNull final Address token,
            @NonNull final Tuple[] adjustments,
            @NonNull final AddressIdConverter addressIdConverter) {
        return convertingAdjustmentsAsGiven(token, adjustments, adjustment -> {
            final Address party = adjustment.get(0);
            final long amount = adjustment.get(1);
            return amount > 0
                    ? credit(addressIdConverter.convertCredit(party), amount)
                    : debit(addressIdConverter.convert(party), -amount, IsApproval.FALSE);
        });
    }

    private TransferList convertingMaybeApprovedAdjustments(
            @NonNull final Tuple[] adjustments, @NonNull final AddressIdConverter addressIdConverter) {
        final var hbarAdjustments = new AccountAmount[adjustments.length];
        for (int i = 0; i < hbarAdjustments.length; i++) {
            hbarAdjustments[i] = asMaybeApprovedAdjustment(adjustments[i], addressIdConverter);
        }
        return TransferList.newBuilder().accountAmounts(hbarAdjustments).build();
    }

    private TokenTransferList convertingMaybeApprovedAdjustments(
            @NonNull final Address token,
            @NonNull final Tuple[] adjustments,
            @NonNull final AddressIdConverter addressIdConverter) {
        return convertingAdjustmentsAsGiven(
                token, adjustments, adjustment -> asMaybeApprovedAdjustment(adjustment, addressIdConverter));
    }

    private TokenTransferList convertingAdjustmentsAsGiven(
            @NonNull final Address token,
            @NonNull final Tuple[] adjustments,
            @NonNull final Function<Tuple, AccountAmount> adjustmentFn) {
        final var tokenId = ConversionUtils.asTokenId(token);
        final var unitAdjustments = new AccountAmount[adjustments.length];
        for (int i = 0; i < unitAdjustments.length; i++) {
            unitAdjustments[i] = adjustmentFn.apply(adjustments[i]);
        }
        return adjustingUnits(tokenId, unitAdjustments);
    }

    private TokenTransferList convertingOwnershipChanges(
            @NonNull final Address token,
            @NonNull final Tuple[] ownershipChanges,
            @NonNull final AddressIdConverter addressIdConverter) {
        final var tokenId = ConversionUtils.asTokenId(token);
        final var nftTransfers = new NftTransfer[ownershipChanges.length];
        for (int i = 0; i < ownershipChanges.length; i++) {
            nftTransfers[i] = nftTransfer(
                    addressIdConverter.convert(ownershipChanges[i].get(0)),
                    addressIdConverter.convertCredit(ownershipChanges[i].get(1)),
                    ownershipChanges[i].get(2),
                    IsApproval.FALSE);
        }
        return changingOwners(tokenId, nftTransfers);
    }

    private TokenTransferList convertingMaybeApprovedOwnershipChanges(
            @NonNull final Address token,
            @NonNull final Tuple[] ownershipChanges,
            @NonNull final AddressIdConverter addressIdConverter) {
        return convertingOwnershipChangesAsGiven(
                token,
                ownershipChanges,
                ownershipChange -> nftTransfer(
                        addressIdConverter.convert(ownershipChange.get(0)),
                        addressIdConverter.convertCredit(ownershipChange.get(1)),
                        ownershipChange.get(2),
                        ownershipChange.get(3) ? IsApproval.TRUE : IsApproval.FALSE));
    }

    private TokenTransferList convertingOwnershipChangesAsGiven(
            @NonNull final Address token,
            @NonNull final Tuple[] ownershipChanges,
            @NonNull final Function<Tuple, NftTransfer> ownershipChangeFn) {
        final var tokenId = ConversionUtils.asTokenId(token);
        final var nftTransfers = new NftTransfer[ownershipChanges.length];
        for (int i = 0; i < ownershipChanges.length; i++) {
            nftTransfers[i] = ownershipChangeFn.apply(ownershipChanges[i]);
        }
        return changingOwners(tokenId, nftTransfers);
    }

    private TokenTransferList convertingAdjustments(
            @NonNull final Address token,
            @NonNull final Address[] party,
            @NonNull final long[] amount,
            @NonNull final AddressIdConverter addressIdConverter) {
        final var tokenId = ConversionUtils.asTokenId(token);
        if (party.length != amount.length) {
            throw new IllegalArgumentException(
                    "Mismatched argument arrays (# party=" + party.length + ", # amount=" + amount.length + ")");
        }
        final var unitAdjustments = new AccountAmount[party.length];
        for (int i = 0; i < party.length; i++) {
            unitAdjustments[i] = amount[i] > 0
                    ? credit(addressIdConverter.convertCredit(party[i]), amount[i])
                    : debit(addressIdConverter.convert(party[i]), -amount[i], IsApproval.FALSE);
        }
        return adjustingUnits(tokenId, unitAdjustments);
    }

    private AccountAmount asMaybeApprovedAdjustment(
            @NonNull final Tuple adjustment, @NonNull final AddressIdConverter addressIdConverter) {
        final Address party = adjustment.get(0);
        final long amount = adjustment.get(1);
        return adjust(
                amount > 0 ? addressIdConverter.convertCredit(party) : addressIdConverter.convert(party),
                amount,
                adjustment.get(2) ? IsApproval.TRUE : IsApproval.FALSE);
    }
}
