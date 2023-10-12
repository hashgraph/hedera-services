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

package token;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Fraction;
import com.hedera.hapi.node.base.NftTransfer;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.token.TokenMintTransactionBody;
import com.hedera.hapi.node.transaction.CustomFee;
import com.hedera.hapi.node.transaction.RoyaltyFee;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import common.AbstractXTest;
import common.BaseScaffoldingComponent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;

public abstract class AbstractTokenXTest extends AbstractXTest {
    protected static final AccountID DEFAULT_PAYER_ID =
            AccountID.newBuilder().accountNum(2L).build();
    protected TokenScaffoldingComponent component;

    @BeforeEach
    void setUp() {
        component = DaggerTokenScaffoldingComponent.factory().create(metrics, configuration());
    }

    protected Configuration configuration() {
        return HederaTestConfigBuilder.create().getOrCreateConfig();
    }

    @Override
    protected BaseScaffoldingComponent component() {
        return component;
    }

    protected Consumer<CryptoTransferTransactionBody.Builder> movingFungibleUnits(
            @NonNull final String token, @NonNull final String from, @NonNull final String to, long amount) {
        return movingFungibleUnits(idOfNamedToken(token), idOfNamedAccount(from), idOfNamedAccount(to), amount);
    }

    protected Consumer<CryptoTransferTransactionBody.Builder> movingFungibleUnits(
            @NonNull final TokenID tokenID, @NonNull final AccountID from, @NonNull final AccountID to, long amount) {
        return b -> {
            final List<TokenTransferList> newTransfers =
                    new ArrayList<>(b.build().tokenTransfersOrThrow());
            newTransfers.add(TokenTransferList.newBuilder()
                    .token(tokenID)
                    .transfers(new AccountAmount(from, -amount, false), new AccountAmount(to, amount, false))
                    .build());
            b.tokenTransfers(newTransfers);
        };
    }

    protected Consumer<CryptoTransferTransactionBody.Builder> movingNft(
            @NonNull final String token, @NonNull final String from, @NonNull final String to, long serialNo) {
        return movingNft(idOfNamedToken(token), idOfNamedAccount(from), idOfNamedAccount(to), serialNo);
    }

    protected Consumer<CryptoTransferTransactionBody.Builder> movingNft(
            @NonNull final TokenID tokenID, @NonNull final AccountID from, @NonNull final AccountID to, long serialNo) {
        return b -> {
            final List<TokenTransferList> newTransfers =
                    new ArrayList<>(b.build().tokenTransfersOrThrow());
            newTransfers.add(TokenTransferList.newBuilder()
                    .token(tokenID)
                    .nftTransfers(new NftTransfer(from, to, serialNo, false))
                    .build());
            b.tokenTransfers(newTransfers);
        };
    }

    protected CustomFee royaltyFeeNoFallback(final long numerator, final long denominator, String collector) {
        return royaltyFeeNoFallback(numerator, denominator, idOfNamedAccount(collector));
    }

    protected CustomFee royaltyFeeNoFallback(final long numerator, final long denominator, AccountID collectorId) {
        return CustomFee.newBuilder()
                .royaltyFee(RoyaltyFee.newBuilder()
                        .exchangeValueFraction(
                                Fraction.newBuilder().numerator(numerator).denominator(denominator))
                        .build())
                .feeCollectorAccountId(collectorId)
                .build();
    }

    @SafeVarargs
    protected final TransactionBody transfer(Consumer<CryptoTransferTransactionBody.Builder>... specs) {
        final var b = CryptoTransferTransactionBody.newBuilder();
        for (final var spec : specs) {
            spec.accept(b);
        }
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(DEFAULT_PAYER_ID))
                .cryptoTransfer(b)
                .build();
    }

    protected final TransactionBody nftMint(@NonNull final Bytes metadata, @NonNull final String token) {
        return nftMint(metadata, idOfNamedToken(token));
    }

    protected final TransactionBody nftMint(@NonNull final Bytes metadata, @NonNull final TokenID tokenID) {
        final var b = TokenMintTransactionBody.newBuilder();
        b.metadata(metadata).token(tokenID);
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(DEFAULT_PAYER_ID))
                .tokenMint(b)
                .build();
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        final var accounts = super.initialAccounts();
        accounts.put(
                DEFAULT_PAYER_ID,
                Account.newBuilder()
                        .accountId(DEFAULT_PAYER_ID)
                        .tinybarBalance(Long.MAX_VALUE / 2)
                        .build());
        return accounts;
    }
}
