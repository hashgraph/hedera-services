/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.AccountID.AccountOneOfType;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.token.TokenAirdropTransactionBody;
import com.hedera.hapi.node.transaction.ExchangeRate;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.hapi.fees.pricing.AssetsLoader;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.config.data.TokensConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#TOKEN_AIRDROP}.
 */
@Singleton
public class TokenAirdropsHandler implements TransactionHandler {

    private final AssetsLoader assetsLoader;

    /**
     * Default constructor for injection.
     */
    @Inject
    public TokenAirdropsHandler(@NonNull final AssetsLoader assetsLoader) {
        this.assetsLoader = requireNonNull(assetsLoader, "The supplied argument 'assetsLoader' must not be null");
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        // todo
    }

    @Override
    public void pureChecks(@NonNull final TransactionBody txn) throws PreCheckException {
        requireNonNull(txn);
        // todo
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        requireNonNull(context);
        final var txn = context.body();
        final var op = txn.tokenAirdrop();
        final var tokensConfig = context.configuration().getConfigData(TokensConfig.class);

        validateTrue(tokensConfig.airdropsEnabled(), NOT_SUPPORTED);
    }

    @NonNull
    @Override
    public Fees calculateFees(@NonNull final FeeContext feeContext) {
        final var body = feeContext.body();
        final var op = body.tokenAirdrop();

        final var defaultAirdropFees = feeContext.feeCalculator(SubType.DEFAULT).calculate();
        // TODO: add a comment why do we need that
        final var cryptoTransferFees =
                CryptoTransferHandler.calculateCryptoTransferFees(feeContext, null, op.tokenTransfers());
        final var autoAccountCreationFees = calculateAccountAutoCreationFees(feeContext, op);
        final var tokenAssociationFees = calculateTokenAssociationFees(feeContext, op);
        // TODO: the combine fee is not working correctly?
        return combineFees(defaultAirdropFees, cryptoTransferFees, autoAccountCreationFees, tokenAssociationFees);
    }

    private Fees calculateTokenAssociationFees(FeeContext feeContext, TokenAirdropTransactionBody op) {
        var tokenAssociationsCount = 0;
        final var tokenRelStore = feeContext.readableStore(ReadableTokenRelationStore.class);
        for (var transferList : op.tokenTransfers()) {
            final var tokenToTransfer = transferList.token();
            for (var transfer : transferList.transfers()) {
                if (tokenRelStore.get(transfer.accountID(), tokenToTransfer) == null) {
                    tokenAssociationsCount++;
                }
            }
            for (var nftTransfer : transferList.nftTransfers()) {
                if (tokenRelStore.get(nftTransfer.receiverAccountID(), tokenToTransfer) == null) {
                    tokenAssociationsCount++;
                }
            }
        }

        long cryptoCreateFixedPrice = getTinybarsFromTinyCents(
                getFixedPriceInTinyCents(
                        com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAssociateToAccount),
                feeContext.exchangeRateInfo().activeRate(feeContext.consensusNow()));

        final var totalNetworkFeeForTokenAssociation = cryptoCreateFixedPrice * tokenAssociationsCount;
        return new Fees(0, totalNetworkFeeForTokenAssociation, 0);
    }

    private Fees calculateAccountAutoCreationFees(FeeContext feeContext, TokenAirdropTransactionBody op) {
        // Getting the count of non-existing account receivers
        final var receivers = new ArrayList<AccountID>();
        for (var transfer : op.tokenTransfers()) {
            transfer.transfers().forEach(t -> receivers.add(t.accountID()));
            transfer.nftTransfers().forEach(t -> receivers.add(t.receiverAccountID()));
        }

        var nonExistingAliasReceiversCount = 0;
        final var accountStore = feeContext.readableStore(ReadableAccountStore.class);
        for (var receiver : receivers) {
            // if the recipient does not exist and they are referred by their public ECDSA key or evm_address
            if (AccountOneOfType.ALIAS.equals(receiver.account().kind())
                    && accountStore.getAccountById(receiver) == null) {
                nonExistingAliasReceiversCount++;
            }
        }

        long cryptoCreateFixedPrice = getTinybarsFromTinyCents(
                getFixedPriceInTinyCents(com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate),
                feeContext.exchangeRateInfo().activeRate(feeContext.consensusNow()));

        final var totalNetworkFeeForAutoAccountCreation = cryptoCreateFixedPrice * nonExistingAliasReceiversCount;
        return new Fees(0, totalNetworkFeeForAutoAccountCreation, 0);
    }

    private long getTinybarsFromTinyCents(final long tinyCents, @NonNull final ExchangeRate rate) {
        final var aMultiplier = BigInteger.valueOf(rate.hbarEquiv());
        final var bDivisor = BigInteger.valueOf(rate.centEquiv());
        return BigInteger.valueOf(tinyCents)
                .multiply(aMultiplier)
                .divide(bDivisor)
                .longValueExact();
    }

    private long getFixedPriceInTinyCents(com.hederahashgraph.api.proto.java.HederaFunctionality functionality) {
        BigDecimal usdFee;
        try {
            usdFee = assetsLoader
                    .loadCanonicalPrices()
                    .get(functionality)
                    .get(com.hederahashgraph.api.proto.java.SubType.DEFAULT);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to load canonical prices", e);
        }

        final var usdToTinyCents = BigDecimal.valueOf(100 * 100_000_000L);
        return usdToTinyCents.multiply(usdFee).longValue();
    }

    private Fees combineFees(Fees... fees) {
        long networkFee = 0;
        long nodeFee = 0;
        long serviceFee = 0;
        for (var fee : fees) {
            networkFee += fee.networkFee();
            nodeFee += fee.nodeFee();
            serviceFee += fee.serviceFee();
        }
        return new Fees(nodeFee, networkFee, serviceFee);
    }
}
