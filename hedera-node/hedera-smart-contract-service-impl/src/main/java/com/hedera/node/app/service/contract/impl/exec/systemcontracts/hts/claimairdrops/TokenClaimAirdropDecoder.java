/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.claimairdrops;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PENDING_AIRDROP_ID_LIST_TOO_LONG;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asTokenId;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.base.PendingAirdropId;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.token.TokenClaimAirdropTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.config.data.TokensConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Arrays;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class TokenClaimAirdropDecoder {

    // Tuple indexes
    private static final int TRANSFER_LIST = 0;
    private static final int SENDER = 0;
    private static final int RECEIVER = 1;
    private static final int TOKEN = 2;
    private static final int SERIAL = 3;
    private static final int HRC_SENDER = 0;
    private static final int HRC_SERIAL = 1;

    @Inject
    public TokenClaimAirdropDecoder() {
        // Dagger2
    }

    public TransactionBody decodeTokenClaimAirdrop(@NonNull final HtsCallAttempt attempt) {
        final var call = TokenClaimAirdropTranslator.CLAIM_AIRDROP.decodeCall(attempt.inputBytes());
        final var maxPendingAirdropsToClaim =
                attempt.configuration().getConfigData(TokensConfig.class).maxAllowedPendingAirdropsToClaim();
        validateFalse(((Tuple[]) call.get(0)).length > maxPendingAirdropsToClaim, PENDING_AIRDROP_ID_LIST_TOO_LONG);

        final var transferList = (Tuple[]) call.get(TRANSFER_LIST);
        final var pendingAirdrops = new ArrayList<PendingAirdropId>();
        Arrays.stream(transferList).forEach(transfer -> {
            final var senderAddress = (Address) transfer.get(SENDER);
            final var receiverAddress = (Address) transfer.get(RECEIVER);
            final var tokenAddress = (Address) transfer.get(TOKEN);
            final var serial = (long) transfer.get(SERIAL);

            final var senderId = attempt.addressIdConverter().convert(senderAddress);
            final var receiverId = attempt.addressIdConverter().convert(receiverAddress);
            final var tokenId = asTokenId(tokenAddress);

            final var token = attempt.enhancement().nativeOperations().getToken(tokenId.tokenNum());
            validateTrue(token != null, INVALID_TOKEN_ID);
            if (token.tokenType().equals(TokenType.FUNGIBLE_COMMON)) {
                pendingAirdrops.add(pendingFTAirdrop(senderId, receiverId, tokenId));
            } else {
                pendingAirdrops.add(pendingNFTAirdrop(senderId, receiverId, tokenId, serial));
            }
        });

        return TransactionBody.newBuilder()
                .tokenClaimAirdrop(TokenClaimAirdropTransactionBody.newBuilder().pendingAirdrops(pendingAirdrops))
                .build();
    }

    public TransactionBody decodeHrcClaimAirdropFt(@NonNull final HtsCallAttempt attempt) {
        final var call = TokenClaimAirdropTranslator.HRC_CLAIM_AIRDROP_FT.decodeCall(attempt.inputBytes());

        // As the Token Claim is an operation for the receiver of an Airdrop,
        // hence the `transaction sender` in the HRC scenario is in reality the `Airdrop receiver`.
        final var receiverId = attempt.senderId();
        final var senderAddress = (Address) call.get(HRC_SENDER);
        final var token = attempt.redirectTokenId();
        validateTrue(token != null, INVALID_TOKEN_ID);
        final var senderId = attempt.addressIdConverter().convert(senderAddress);

        return TransactionBody.newBuilder()
                .tokenClaimAirdrop(TokenClaimAirdropTransactionBody.newBuilder()
                        .pendingAirdrops(pendingFTAirdrop(senderId, receiverId, token)))
                .build();
    }

    public TransactionBody decodeHrcClaimAirdropNft(@NonNull final HtsCallAttempt attempt) {
        final var call = TokenClaimAirdropTranslator.HRC_CLAIM_AIRDROP_NFT.decodeCall(attempt.inputBytes());

        // As the Token Claim is an operation for the receiver of an Airdrop,
        // hence the `transaction sender` in the HRC scenario is in reality the `Airdrop receiver`.
        final var receiverId = attempt.senderId();
        final var senderAddress = (Address) call.get(HRC_SENDER);
        final var serial = (long) call.get(HRC_SERIAL);
        final var token = attempt.redirectTokenId();
        validateTrue(token != null, INVALID_TOKEN_ID);
        final var senderId = attempt.addressIdConverter().convert(senderAddress);

        return TransactionBody.newBuilder()
                .tokenClaimAirdrop(TokenClaimAirdropTransactionBody.newBuilder()
                        .pendingAirdrops(pendingNFTAirdrop(senderId, receiverId, token, serial)))
                .build();
    }

    private PendingAirdropId pendingFTAirdrop(
            @NonNull final AccountID senderId, @NonNull final AccountID receiverId, @NonNull final TokenID tokenId) {
        return PendingAirdropId.newBuilder()
                .senderId(senderId)
                .receiverId(receiverId)
                .fungibleTokenType(tokenId)
                .build();
    }

    private PendingAirdropId pendingNFTAirdrop(
            @NonNull final AccountID senderId,
            @NonNull final AccountID receiverId,
            @NonNull final TokenID tokenId,
            final long serial) {
        return PendingAirdropId.newBuilder()
                .senderId(senderId)
                .receiverId(receiverId)
                .nonFungibleToken(NftID.newBuilder().tokenId(tokenId).serialNumber(serial))
                .build();
    }
}
