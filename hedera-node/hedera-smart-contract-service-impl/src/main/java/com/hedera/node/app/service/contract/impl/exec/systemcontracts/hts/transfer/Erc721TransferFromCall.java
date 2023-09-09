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

import static com.hedera.hapi.node.base.TokenType.NON_FUNGIBLE_UNIQUE;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.revertResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.HederaSystemContract.FullResult.successResult;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall.PricedResult.gasOnly;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer.SynthIdHelper.SYNTH_ID_HELPER;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asHeadlongAddress;
import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategies;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.token.records.CryptoTransferRecordBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.util.Arrays;

public class Erc721TransferFromCall extends AbstractHtsCall {
    public static final Function TRANSFER_FROM = new Function("transferFrom(address,address,uint256)");

    private final long serialNo;
    private final Address from;
    private final Address to;
    private final TokenID tokenId;
    private final VerificationStrategy verificationStrategy;
    private final org.hyperledger.besu.datatypes.Address spender;
    private final SynthIdHelper synthIdHelper;

    public Erc721TransferFromCall(
            final long serialNo,
            @NonNull final Address from,
            @NonNull final Address to,
            @NonNull final TokenID tokenId,
            @NonNull final VerificationStrategy verificationStrategy,
            @NonNull final org.hyperledger.besu.datatypes.Address spender,
            @NonNull final SynthIdHelper synthIdHelper,
            @NonNull final HederaWorldUpdater.Enhancement enhancement) {
        super(enhancement);
        this.from = requireNonNull(from);
        this.to = requireNonNull(to);
        this.tokenId = tokenId;
        this.spender = requireNonNull(spender);
        this.synthIdHelper = requireNonNull(synthIdHelper);
        this.verificationStrategy = requireNonNull(verificationStrategy);

        this.serialNo = serialNo;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @NonNull PricedResult execute() {
        // C.f. https://eips.ethereum.org/EIPS/eip-721
        // TODO - gas calculation
        final var spenderId =
                synthIdHelper.syntheticIdFor(asHeadlongAddress(spender.toArrayUnsafe()), nativeOperations());
        final var recordBuilder = systemContractOperations()
                .dispatch(
                        syntheticTransfer(spenderId),
                        verificationStrategy,
                        spenderId,
                        CryptoTransferRecordBuilder.class);
        if (recordBuilder.status() != ResponseCodeEnum.SUCCESS) {
            return gasOnly(revertResult(recordBuilder.status(), 0L));
        } else {
            return gasOnly(successResult(TRANSFER_FROM.getOutputs().encodeElements(), 0L));
        }
    }

    private TransactionBody syntheticTransfer(@NonNull final AccountID spenderId) {
        final var nativeOperations = enhancement.nativeOperations();
        final var ownerId = synthIdHelper.syntheticIdFor(from, nativeOperations);
        final var receiverId = synthIdHelper.syntheticIdForCredit(to, nativeOperations);
        return TransactionBody.newBuilder()
                .cryptoTransfer(CryptoTransferTransactionBody.newBuilder()
                        .tokenTransfers(TokenTransferList.newBuilder()
                                .token(tokenId)
                                .nftTransfers(NftTransfer.newBuilder()
                                        .serialNumber(serialNo)
                                        .senderAccountID(ownerId)
                                        .receiverAccountID(receiverId)
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
        return attempt.isTokenRedirect()
                && Arrays.equals(attempt.selector(), TRANSFER_FROM.selector())
                && attempt.redirectToken() != null
                && requireNonNull(attempt.redirectToken()).tokenType() == NON_FUNGIBLE_UNIQUE;
    }

    /**
     * Creates a {@link Erc721TransferFromCall} from the given {@code attempt} and {@code senderAddress}.
     *
     * @param attempt the attempt to create a {@link TransferCall} from
     * @param sender  the address of the caller
     * @return a {@link Erc721TransferFromCall} if the given {@code attempt} is a valid {@link TransferCall}, otherwise {@code null}
     */
    public static Erc721TransferFromCall from(
            @NonNull final HtsCallAttempt attempt,
            @NonNull final org.hyperledger.besu.datatypes.Address sender,
            final boolean senderNeedsDelegatableContractKeys,
            @NonNull final VerificationStrategies verificationStrategies) {
        final var call = TRANSFER_FROM.decodeCall(attempt.input().toArrayUnsafe());
        return new Erc721TransferFromCall(
                ((BigInteger) call.get(2)).longValueExact(),
                call.get(0),
                call.get(1),
                requireNonNull(attempt.redirectToken()).tokenIdOrThrow(),
                verificationStrategies.onlyActivatingContractKeys(
                        sender, attempt.enhancement().nativeOperations(), senderNeedsDelegatableContractKeys),
                sender,
                SYNTH_ID_HELPER,
                attempt.enhancement());
    }
}
