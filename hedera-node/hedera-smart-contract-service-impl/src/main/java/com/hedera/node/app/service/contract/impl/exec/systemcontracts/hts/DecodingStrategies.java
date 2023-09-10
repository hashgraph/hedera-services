/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts;

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.explicitFromHeadlong;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.numberOfLongZero;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.ClassicTransfersCall;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Encapsulates some strategies of decoding ABI calls, extracted here to ease unit testing.
 */
@Singleton
public class DecodingStrategies {
    @Inject
    public DecodingStrategies() {
        // Dagger2
    }

    enum IsApproval {
        TRUE,
        FALSE
    }

    /**
     * Decodes a call to {@link ClassicTransfersCall#CRYPTO_TRANSFER} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeCryptoTransfer(
            @NonNull final byte[] encoded, @NonNull final AddressIdConverter addressIdConverter) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Decodes a call to {@link ClassicTransfersCall#CRYPTO_TRANSFER_V2} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeCryptoTransferV2(
            @NonNull final byte[] encoded, @NonNull final AddressIdConverter addressIdConverter) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Decodes a call to {@link ClassicTransfersCall#TRANSFER_TOKENS} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeTransferTokens(
            @NonNull final byte[] encoded, @NonNull final AddressIdConverter addressIdConverter) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Decodes a call to {@link ClassicTransfersCall#TRANSFER_TOKEN} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeTransferToken(
            @NonNull final byte[] encoded, @NonNull final AddressIdConverter addressIdConverter) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Decodes a call to {@link ClassicTransfersCall#TRANSFER_NFTS} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeTransferNfts(
            @NonNull final byte[] encoded, @NonNull final AddressIdConverter addressIdConverter) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Decodes a call to {@link ClassicTransfersCall#TRANSFER_NFT} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeTransferNft(
            @NonNull final byte[] encoded, @NonNull final AddressIdConverter addressIdConverter) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Decodes a call to {@link ClassicTransfersCall#HRC_TRANSFER_FROM} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeHrcTransferFrom(
            @NonNull final byte[] encoded, @NonNull final AddressIdConverter addressIdConverter) {
        throw new AssertionError("Not implemented");
    }

    /**
     * Decodes a call to {@link ClassicTransfersCall#HRC_TRANSFER_NFT_FROM} into a synthetic {@link TransactionBody}.
     *
     * @param encoded the encoded call
     * @return the synthetic transaction body
     */
    public TransactionBody decodeHrcTransferNftFrom(
            @NonNull final byte[] encoded, @NonNull final AddressIdConverter addressIdConverter) {
        final var call = ClassicTransfersCall.HRC_TRANSFER_NFT_FROM.decodeCall(encoded);
        return bodyOf(tokenTransfers(ownershipChanges(
                asTokenId(call.get(0)),
                addressIdConverter.convert(call.get(1)),
                addressIdConverter.convertCredit(call.get(2)),
                exactLongValueOrThrow(call.get(3)),
                IsApproval.TRUE)));
    }

    private CryptoTransferTransactionBody.Builder tokenTransfers(
            @NonNull final TokenTransferList... tokenTransferList) {
        return CryptoTransferTransactionBody.newBuilder().tokenTransfers(tokenTransferList);
    }

    private TokenTransferList ownershipChanges(
            @NonNull final TokenID tokenId,
            @NonNull final AccountID from,
            @NonNull final AccountID to,
            final long serialNo,
            final IsApproval isApproval) {
        return TokenTransferList.newBuilder()
                .token(tokenId)
                .nftTransfers(ownershipChange(from, to, serialNo, isApproval))
                .build();
    }

    private NftTransfer ownershipChange(
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

    private TokenID asTokenId(@NonNull final Address address) {
        // Mono-service ignores the shard and realm, c.f. DecodingFacade#convertAddressBytesToTokenID(),
        // so we continue to do that here; might want to revisit this later
        return TokenID.newBuilder()
                .tokenNum(numberOfLongZero(explicitFromHeadlong(address)))
                .build();
    }
}
