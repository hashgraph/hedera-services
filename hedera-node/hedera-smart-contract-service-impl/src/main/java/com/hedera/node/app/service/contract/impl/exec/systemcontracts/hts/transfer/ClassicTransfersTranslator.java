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

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.ApprovalSwitchHelper.APPROVAL_SWITCH_HELPER;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.CallStatusStandardizer.CALL_STATUS_STANDARDIZER;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.SpecialRewardReceivers.SPECIAL_REWARD_RECEIVERS;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.SystemAccountCreditScreen.SYSTEM_ACCOUNT_CREDIT_SCREEN;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Translates "classic" {@code cryptoTransfer()} calls to the HTS system contract.
 */
@Singleton
public class ClassicTransfersTranslator extends AbstractCallTranslator<HtsCallAttempt> {
    /**
     * Selector for cryptoTransfer((address,(address,int64)[],(address,address,int64)[])[]) method.
     */
    public static final Function CRYPTO_TRANSFER =
            new Function("cryptoTransfer((address,(address,int64)[],(address,address,int64)[])[])", ReturnTypes.INT_64);
    /**
     * Selector for cryptoTransfer(((address,int64,bool)[]),(address,(address,int64,bool)[],(address,address,int64,bool)[])[]) method.
     */
    public static final Function CRYPTO_TRANSFER_V2 = new Function(
            "cryptoTransfer(((address,int64,bool)[]),(address,(address,int64,bool)[],(address,address,int64,bool)[])[])",
            ReturnTypes.INT_64);
    /**
     * Selector for transferTokens(address,address[],int64[]) method.
     */
    public static final Function TRANSFER_TOKENS =
            new Function("transferTokens(address,address[],int64[])", ReturnTypes.INT_64);
    /**
     * Selector for transferToken(address,address,address,int64) method.
     */
    public static final Function TRANSFER_TOKEN =
            new Function("transferToken(address,address,address,int64)", ReturnTypes.INT_64);
    /**
     * Selector for transferNFTs(address,address[],address[],int64[]) method.
     */
    public static final Function TRANSFER_NFTS =
            new Function("transferNFTs(address,address[],address[],int64[])", ReturnTypes.INT_64);
    /**
     * Selector for transferNFT(address,address,address,int64) method.
     */
    public static final Function TRANSFER_NFT =
            new Function("transferNFT(address,address,address,int64)", ReturnTypes.INT_64);
    /**
     * Selector for transferFrom(address,address,address,uint256) method.
     */
    public static final Function TRANSFER_FROM =
            new Function("transferFrom(address,address,address,uint256)", ReturnTypes.INT_64);
    /**
     * Selector for transferFromNFT(address,address,address,uint256) method.
     */
    public static final Function TRANSFER_NFT_FROM =
            new Function("transferFromNFT(address,address,address,uint256)", ReturnTypes.INT_64);

    private final ClassicTransfersDecoder decoder;

    /**
     * Constructor for injection.
     * @param decoder the decoder used to decode transfer calls
     */
    @Inject
    public ClassicTransfersTranslator(ClassicTransfersDecoder decoder) {
        this.decoder = decoder;
    }

    @Override
    public boolean matches(@NonNull final HtsCallAttempt attempt) {
        return !attempt.isTokenRedirect()
                && (attempt.isSelector(CRYPTO_TRANSFER, CRYPTO_TRANSFER_V2)
                        || attempt.isSelector(TRANSFER_TOKENS, TRANSFER_TOKEN)
                        || attempt.isSelector(TRANSFER_NFTS, TRANSFER_NFT)
                        || attempt.isSelector(TRANSFER_FROM, TRANSFER_NFT_FROM));
    }

    @Override
    public ClassicTransfersCall callFrom(@NonNull final HtsCallAttempt attempt) {
        final var selector = attempt.selector();
        return new ClassicTransfersCall(
                attempt.systemContractGasCalculator(),
                attempt.enhancement(),
                selector,
                // This is the only place we don't use the EVM sender id, because
                // we need to switch debits to approvals based on whether the
                // mono-service would have activated a key; and its key activation
                // test would use the qualified delegate id if applicable
                // test would use the qualified delegate id if applicable.
                // Only certain functions support qualified delegates, so restrict to those.
                isClassicCallSupportingQualifiedDelegate(attempt) ? attempt.authorizingId() : attempt.senderId(),
                decoder.checkForFailureStatus(attempt),
                nominalBodyFor(attempt),
                attempt.configuration(),
                isClassicCall(attempt) ? APPROVAL_SWITCH_HELPER : null,
                CALL_STATUS_STANDARDIZER,
                attempt.defaultVerificationStrategy(),
                SYSTEM_ACCOUNT_CREDIT_SCREEN,
                SPECIAL_REWARD_RECEIVERS);
    }

    private @Nullable TransactionBody nominalBodyFor(@NonNull final HtsCallAttempt attempt) {
        if (attempt.isSelector(CRYPTO_TRANSFER)) {
            return decoder.decodeCryptoTransfer(attempt.inputBytes(), attempt.addressIdConverter());
        } else if (attempt.isSelector(CRYPTO_TRANSFER_V2)) {
            return decoder.decodeCryptoTransferV2(attempt.inputBytes(), attempt.addressIdConverter());
        } else if (attempt.isSelector(TRANSFER_TOKENS)) {
            return decoder.decodeTransferTokens(attempt.inputBytes(), attempt.addressIdConverter());
        } else if (attempt.isSelector(TRANSFER_TOKEN)) {
            return decoder.decodeTransferToken(attempt.inputBytes(), attempt.addressIdConverter());
        } else if (attempt.isSelector(TRANSFER_NFTS)) {
            return decoder.decodeTransferNfts(attempt.inputBytes(), attempt.addressIdConverter());
        } else if (attempt.isSelector(TRANSFER_NFT)) {
            return decoder.decodeTransferNft(attempt.inputBytes(), attempt.addressIdConverter());
        } else if (attempt.isSelector(TRANSFER_FROM)) {
            return decoder.decodeHrcTransferFrom(attempt.inputBytes(), attempt.addressIdConverter());
        } else {
            return decoder.decodeHrcTransferNftFrom(attempt.inputBytes(), attempt.addressIdConverter());
        }
    }

    private boolean isClassicCall(@NonNull final HtsCallAttempt attempt) {
        return attempt.isSelector(
                CRYPTO_TRANSFER, CRYPTO_TRANSFER_V2, TRANSFER_TOKENS, TRANSFER_TOKEN, TRANSFER_NFTS, TRANSFER_NFT);
    }

    private boolean isClassicCallSupportingQualifiedDelegate(@NonNull final HtsCallAttempt attempt) {
        return attempt.isSelector(TRANSFER_TOKENS, TRANSFER_TOKEN, TRANSFER_NFTS, TRANSFER_NFT);
    }
}
