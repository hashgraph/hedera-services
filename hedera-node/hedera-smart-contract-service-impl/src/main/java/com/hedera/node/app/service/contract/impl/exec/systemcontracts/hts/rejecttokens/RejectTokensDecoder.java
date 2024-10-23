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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.rejecttokens;

import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_REFERENCE_LIST_SIZE_LIMIT_EXCEEDED;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asTokenId;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.NftID;
import com.hedera.hapi.node.token.TokenReference;
import com.hedera.hapi.node.token.TokenRejectTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.config.data.LedgerConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class RejectTokensDecoder {

    // Tuple indexes
    private static final int FUNGIBLE_ADDRESS_INDEX = 0;
    private static final int NFT_IDS_INDEX = 1;
    private static final int NFT_ID_ADDRESS_INDEX = 0;
    private static final int NFT_ID_SERIAL_INDEX = 1;
    private static final int HRC_NFT_SERIAL_INDEX = 0;

    @Inject
    public RejectTokensDecoder() {
        // Dagger2
    }

    public TransactionBody decodeTokenRejects(@NonNull final HtsCallAttempt attempt) {
        final var call = RejectTokensTranslator.TOKEN_REJECT.decodeCall(attempt.inputBytes());
        final var maxRejections =
                attempt.configuration().getConfigData(LedgerConfig.class).tokenRejectsMaxLen();
        final Address[] ftAddresses = call.get(FUNGIBLE_ADDRESS_INDEX);
        final Tuple[] nftIds = call.get(NFT_IDS_INDEX);
        validateFalse(ftAddresses.length + nftIds.length > maxRejections, TOKEN_REFERENCE_LIST_SIZE_LIMIT_EXCEEDED);
        final var sender = attempt.senderId();
        var referenceList = new ArrayList<TokenReference>();
        for (Address ftAddress : ftAddresses) {
            final var tokenReference = TokenReference.newBuilder()
                    .fungibleToken(asTokenId(ftAddress))
                    .build();
            referenceList.add(tokenReference);
        }
        for (Tuple nftId : nftIds) {
            final var nftIdAddress = (Address) nftId.get(NFT_ID_ADDRESS_INDEX);
            final var nftIdSerial = (long) nftId.get(NFT_ID_SERIAL_INDEX);
            final var nftReference = TokenReference.newBuilder()
                    .nft(NftID.newBuilder()
                            .tokenId(asTokenId(nftIdAddress))
                            .serialNumber(nftIdSerial)
                            .build())
                    .build();
            referenceList.add(nftReference);
        }

        return TransactionBody.newBuilder()
                .tokenReject(
                        TokenRejectTransactionBody.newBuilder().owner(sender).rejections(referenceList))
                .build();
    }

    public TransactionBody decodeHrcTokenRejectFT(@NonNull final HtsCallAttempt attempt) {
        final var token = attempt.redirectTokenId();
        final var sender = attempt.senderId();
        final var tokenReference =
                TokenReference.newBuilder().fungibleToken(token).build();
        return TransactionBody.newBuilder()
                .tokenReject(
                        TokenRejectTransactionBody.newBuilder().owner(sender).rejections(tokenReference))
                .build();
    }

    public TransactionBody decodeHrcTokenRejectNFT(@NonNull final HtsCallAttempt attempt) {
        final var maxRejections =
                attempt.configuration().getConfigData(LedgerConfig.class).tokenRejectsMaxLen();
        final var call = RejectTokensTranslator.HRC_TOKEN_REJECT_NFT.decodeCall(attempt.inputBytes());
        final var serials = (long[]) call.get(HRC_NFT_SERIAL_INDEX);
        validateFalse(serials.length > maxRejections, TOKEN_REFERENCE_LIST_SIZE_LIMIT_EXCEEDED);
        final var token = attempt.redirectTokenId();
        final var sender = attempt.senderId();
        var referenceList = new ArrayList<TokenReference>();
        for (long serial : serials) {
            final var tokenReference = TokenReference.newBuilder()
                    .nft(NftID.newBuilder().tokenId(token).serialNumber(serial).build())
                    .build();
            referenceList.add(tokenReference);
        }
        return TransactionBody.newBuilder()
                .tokenReject(
                        TokenRejectTransactionBody.newBuilder().owner(sender).rejections(referenceList))
                .build();
    }
}
