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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer;

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.ApprovalSwitchHelper.APPROVAL_SWITCH_HELPER;
import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.swirlds.common.utility.CommonUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Arrays;

/**
 * Implements the "classic" HTS transfer calls, which differ from the ERC redirects in three notable ways:
 * <ol>
 *     <li>They accept the token address as an explicit parameter, instead of getting the token id
 *     via a redirect.</li>
 *     <li>They return the ordinal value of a non-successful {@link ResponseCodeEnum} instead of reverting
 *     like the ERC calls do.</li>
 *     <li>The legacy versions that don't support approvals will automatically "retry" their synthetic
 *     transaction using approvals for all non-sender debits if the initial attempt fails with
 *     {@link ResponseCodeEnum#INVALID_SIGNATURE} (which they translate to
 *     {@link ResponseCodeEnum#INVALID_FULL_PREFIX_SIGNATURE_FOR_PRECOMPILE}
 *     for backward compatibility).</li>
 * </ol>
 * But the basic pattern of constructing and dispatching a synthetic {@link CryptoTransferTransactionBody} remains.
 */
public class ClassicTransfersCall extends AbstractHtsCall {
    public static final Function CRYPTO_TRANSFER =
            new Function("cryptoTransfer((address,(address,int64)[],(address,address,int64)[])[])", ReturnTypes.INT);
    public static final Function CRYPTO_TRANSFER_V2 = new Function(
            "cryptoTransfer(((address,int64,bool)[]),(address,(address,int64,bool)[],(address,address,int64,bool)[])[])",
            ReturnTypes.INT);
    public static final Function TRANSFER_TOKENS =
            new Function("transferTokens(address,address[],int64[])", ReturnTypes.INT);
    public static final Function TRANSFER_TOKEN =
            new Function("transferToken(address,address,address,int64)", ReturnTypes.INT);
    public static final Function TRANSFER_NFTS =
            new Function("transferNFTs(address,address[],address[],int64[])", ReturnTypes.INT);
    public static final Function TRANSFER_NFT =
            new Function("transferNFT(address,address,address,int64)", ReturnTypes.INT);
    public static final Function HRC_TRANSFER_FROM =
            new Function("transferFrom(address,address,address,uint256)", ReturnTypes.INT);
    public static final Function HRC_TRANSFER_NFT_FROM =
            new Function("transferFromNFT(address,address,address,uint256)", ReturnTypes.INT);

    private final TransactionBody nominalBody;
    @Nullable
    private final ApprovalSwitchHelper approvalSwitchHelper;

    private final VerificationStrategy verificationStrategy;

    public ClassicTransfersCall(
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final TransactionBody nominalBody,
            @Nullable ApprovalSwitchHelper approvalSwitchHelper,
            @NonNull final VerificationStrategy verificationStrategy) {
        super(enhancement);
        this.nominalBody = requireNonNull(nominalBody);
        this.approvalSwitchHelper = approvalSwitchHelper;
        this.verificationStrategy = requireNonNull(verificationStrategy);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull PricedResult execute() {
        throw new AssertionError("Not implemented");
    }

    /**
     * Indicates if the given {@code selector} is a selector for {@link ClassicTransfersCall}.
     *
     * @param selector the selector to check
     * @return {@code true} if the given {@code selector} is a selector for {@link ClassicTransfersCall}
     */
    public static boolean matches(@NonNull final byte[] selector) {
        requireNonNull(selector);
        return Arrays.equals(selector, CRYPTO_TRANSFER.selector())
                || Arrays.equals(selector, CRYPTO_TRANSFER_V2.selector())
                || Arrays.equals(selector, TRANSFER_TOKENS.selector())
                || Arrays.equals(selector, TRANSFER_TOKEN.selector())
                || Arrays.equals(selector, TRANSFER_NFTS.selector())
                || Arrays.equals(selector, TRANSFER_NFT.selector())
                || Arrays.equals(selector, HRC_TRANSFER_FROM.selector())
                || Arrays.equals(selector, HRC_TRANSFER_NFT_FROM.selector());
    }

    /**
     * Creates a {@link ClassicTransfersCall} from the given {@code attempt} and {@code senderAddress}.
     *
     * @param attempt         the attempt to create a {@link ClassicTransfersCall} from
     * @param sender          the address of the sender
     * @param onlyDelegatable whether the sender needs delegatable contract keys
     * @return a {@link ClassicTransfersCall} if the given {@code attempt} is a valid {@link ClassicTransfersCall}, otherwise {@code null}
     */
    public static ClassicTransfersCall from(
            @NonNull final HtsCallAttempt attempt,
            @NonNull final org.hyperledger.besu.datatypes.Address sender,
            final boolean onlyDelegatable) {
        requireNonNull(attempt);
        requireNonNull(sender);
        return new ClassicTransfersCall(
                attempt.enhancement(),
                nominalBodyFor(attempt),
                isLegacyCall(attempt.selector()) ? APPROVAL_SWITCH_HELPER : null,
                attempt.verificationStrategies()
                        .activatingContractKeysFor(sender, onlyDelegatable, attempt.nativeOperations()));
    }

    private static boolean isLegacyCall(@NonNull final byte[] selector) {
        return Arrays.equals(selector, CRYPTO_TRANSFER.selector())
                || Arrays.equals(selector, TRANSFER_TOKENS.selector())
                || Arrays.equals(selector, TRANSFER_TOKEN.selector())
                || Arrays.equals(selector, TRANSFER_NFTS.selector())
                || Arrays.equals(selector, TRANSFER_NFT.selector());
    }

    private static TransactionBody nominalBodyFor(@NonNull final HtsCallAttempt attempt) {
        if (Arrays.equals(attempt.selector(), CRYPTO_TRANSFER.selector())) {
            return attempt.decodingStrategies()
                    .decodeCryptoTransfer(attempt.input().toArrayUnsafe(), attempt.addressIdConverter());
        } else if (Arrays.equals(attempt.selector(), CRYPTO_TRANSFER_V2.selector())) {
            return attempt.decodingStrategies()
                    .decodeCryptoTransferV2(attempt.input().toArrayUnsafe(), attempt.addressIdConverter());
        } else if (Arrays.equals(attempt.selector(), TRANSFER_TOKENS.selector())) {
            return attempt.decodingStrategies()
                    .decodeTransferTokens(attempt.input().toArrayUnsafe(), attempt.addressIdConverter());
        } else if (Arrays.equals(attempt.selector(), TRANSFER_TOKEN.selector())) {
            return attempt.decodingStrategies()
                    .decodeTransferToken(attempt.input().toArrayUnsafe(), attempt.addressIdConverter());
        } else if (Arrays.equals(attempt.selector(), TRANSFER_NFTS.selector())) {
            return attempt.decodingStrategies()
                    .decodeTransferNfts(attempt.input().toArrayUnsafe(), attempt.addressIdConverter());
        } else if (Arrays.equals(attempt.selector(), TRANSFER_NFT.selector())) {
            return attempt.decodingStrategies()
                    .decodeTransferNft(attempt.input().toArrayUnsafe(), attempt.addressIdConverter());
        } else if (Arrays.equals(attempt.selector(), HRC_TRANSFER_FROM.selector())) {
            return attempt.decodingStrategies()
                    .decodeHrcTransferFrom(attempt.input().toArrayUnsafe(), attempt.addressIdConverter());
        } else if (Arrays.equals(attempt.selector(), HRC_TRANSFER_NFT_FROM.selector())) {
            return attempt.decodingStrategies()
                    .decodeHrcTransferNftFrom(attempt.input().toArrayUnsafe(), attempt.addressIdConverter());
        } else {
            throw new IllegalArgumentException(
                    "Selector " + CommonUtils.hex(attempt.selector()) + "is not a classic transfer");
        }
    }
}
