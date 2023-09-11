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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hedera.hapi.node.base.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall.PricedResult.gasOnly;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asHeadlongAddress;
import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.token.records.CryptoTransferRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.math.BigInteger;
import java.util.Arrays;

/**
 * Implements the ERC-20 {@code transfer()} and {@code transferFrom()} calls of the HTS contract.
 */
public class Erc20TransfersCall extends AbstractHtsCall {
    public static final Function ERC_20_TRANSFER = new Function("transfer(address,uint256)", ReturnTypes.BOOL);
    public static final Function ERC_20_TRANSFER_FROM =
            new Function("transferFrom(address,address,uint256)", ReturnTypes.BOOL);

    private final long amount;

    @Nullable
    private final Address from;

    private final Address to;

    @Nullable
    private final TokenID tokenId;

    private final VerificationStrategy verificationStrategy;
    private final org.hyperledger.besu.datatypes.Address spender;
    private final AddressIdConverter addressIdConverter;

    // too many parameters
    @SuppressWarnings("java:S107")
    public Erc20TransfersCall(
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            final long amount,
            @Nullable final Address from,
            @NonNull final Address to,
            @Nullable final TokenID tokenId,
            @NonNull final VerificationStrategy verificationStrategy,
            @NonNull final org.hyperledger.besu.datatypes.Address spender,
            @NonNull final AddressIdConverter addressIdConverter) {
        super(enhancement);
        this.amount = amount;
        this.from = from;
        this.to = requireNonNull(to);
        this.tokenId = tokenId;
        this.verificationStrategy = requireNonNull(verificationStrategy);
        this.spender = requireNonNull(spender);
        this.addressIdConverter = requireNonNull(addressIdConverter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull PricedResult execute() {
        // https://eips.ethereum.org/EIPS/eip-20
        // TODO - gas calculation
        if (tokenId == null) {
            return reversionWith(INVALID_TOKEN_ID, 0L);
        }
        final var spenderId = addressIdConverter.convert(asHeadlongAddress(spender.toArrayUnsafe()));
        final var recordBuilder = systemContractOperations()
                .dispatch(
                        syntheticTransferOrTransferFrom(spenderId),
                        verificationStrategy,
                        spenderId,
                        CryptoTransferRecordBuilder.class);
        if (recordBuilder.status() != ResponseCodeEnum.SUCCESS) {
            return gasOnly(revertResult(recordBuilder.status(), 0L));
        } else {
            final var encodedOutput = (from == null)
                    ? ERC_20_TRANSFER.getOutputs().encodeElements(true)
                    : ERC_20_TRANSFER_FROM.getOutputs().encodeElements(true);
            return gasOnly(successResult(encodedOutput, 0L));
        }
    }

    private TransactionBody syntheticTransferOrTransferFrom(@NonNull final AccountID spenderId) {
        final var receiverId = addressIdConverter.convertCredit(to);
        final var ownerId = (from == null) ? spenderId : addressIdConverter.convert(from);
        return TransactionBody.newBuilder()
                .cryptoTransfer(CryptoTransferTransactionBody.newBuilder()
                        .tokenTransfers(TokenTransferList.newBuilder()
                                .token(tokenId)
                                .transfers(
                                        AccountAmount.newBuilder()
                                                .accountID(receiverId)
                                                .amount(amount)
                                                .build(),
                                        AccountAmount.newBuilder()
                                                .accountID(ownerId)
                                                .amount(-amount)
                                                .isApproval(!spenderId.equals(ownerId))
                                                .build())
                                .build()))
                .build();
    }

    /**
     * Indicates if the given {@link HtsCallAttempt} is an {@link Erc721TransferFromCall}.
     *
     * @param attempt the attempt to check
     * @return {@code true} if the given {@code attempt} is an {@link Erc721TransferFromCall}, otherwise {@code false}
     */
    public static boolean matches(@NonNull final HtsCallAttempt attempt) {
        requireNonNull(attempt);
        // We will match the transferFrom() selector shared by ERC-20 and ERC-721 if the token is missing
        return attempt.isTokenRedirect()
                && selectorsInclude(attempt.selector())
                && attempt.redirectTokenType() != NON_FUNGIBLE_UNIQUE;
    }

    /**
     * Creates a {@link Erc20TransfersCall} from the given {@code attempt} and {@code senderAddress}.
     *
     * @param attempt the attempt to create a {@link Erc20TransfersCall} from
     * @param sender  the address of the caller
     * @return the appropriate {@link Erc20TransfersCall}
     */
    public static Erc20TransfersCall from(
            @NonNull final HtsCallAttempt attempt,
            @NonNull final org.hyperledger.besu.datatypes.Address sender,
            final boolean senderNeedsDelegatableContractKeys) {
        if (isErc20Transfer(attempt.selector())) {
            final var call = ERC_20_TRANSFER.decodeCall(attempt.input().toArrayUnsafe());
            return callFrom(sender, senderNeedsDelegatableContractKeys, null, call.get(0), call.get(1), attempt);
        } else {
            final var call = ERC_20_TRANSFER_FROM.decodeCall(attempt.input().toArrayUnsafe());
            return callFrom(sender, senderNeedsDelegatableContractKeys, call.get(0), call.get(1), call.get(2), attempt);
        }
    }

    private static Erc20TransfersCall callFrom(
            @NonNull final org.hyperledger.besu.datatypes.Address sender,
            final boolean senderNeedsDelegatableContractKeys,
            @Nullable final Address from,
            @NonNull final Address to,
            @NonNull final BigInteger amount,
            @NonNull final HtsCallAttempt attempt) {
        return new Erc20TransfersCall(
                attempt.enhancement(),
                amount.longValueExact(),
                from,
                to,
                requireNonNull(attempt.redirectToken()).tokenIdOrThrow(),
                attempt.verificationStrategies()
                        .activatingOnlyContractKeysFor(
                                sender,
                                senderNeedsDelegatableContractKeys,
                                attempt.enhancement().nativeOperations()),
                sender,
                attempt.addressIdConverter());
    }

    private static boolean selectorsInclude(@NonNull final byte[] selector) {
        return isErc20Transfer(selector) || isErc20TransferFrom(selector);
    }

    private static boolean isErc20Transfer(@NonNull final byte[] selector) {
        return Arrays.equals(selector, ERC_20_TRANSFER.selector());
    }

    private static boolean isErc20TransferFrom(@NonNull final byte[] selector) {
        return Arrays.equals(selector, ERC_20_TRANSFER_FROM.selector());
    }
}
