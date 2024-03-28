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

package token;

import com.hedera.hapi.node.base.*;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.hapi.node.token.TokenGetInfoQuery;
import com.hedera.hapi.node.token.TokenMintTransactionBody;
import com.hedera.hapi.node.token.TokenUpdateTransactionBody;
import com.hedera.hapi.node.transaction.*;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import common.AbstractXTest;
import common.BaseScaffoldingComponent;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import static contract.XTestConstants.AN_ED25519_KEY;
import static org.assertj.core.api.Assertions.assertThat;

public abstract class AbstractTokenXTest extends AbstractXTest {
    protected static final AccountID DEFAULT_PAYER_ID =
            AccountID.newBuilder().accountNum(2L).build();
    private Key DEFAULT_PAYER_KEY = Key.newBuilder().ed25519(Bytes.fromHex("00aaa00aaa00aaa00aaa00aaa00aaaaa00aaa00aaa00aaa00aaa00aaa00aaaaa")).build();
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

    protected final TransactionBody tokenUpdate(final TokenID tokenID, List<Consumer<TokenUpdateTransactionBody.Builder>> methods) {
        final var b = TokenUpdateTransactionBody.newBuilder();
        b.token(tokenID);
        for (final var method : methods) {
            method.accept(b);
        }
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(DEFAULT_PAYER_ID))
                .tokenUpdate(b)
                .build();
    }

    protected final Query tokenInfo(final TokenID tokenID) {
        final var b = TokenGetInfoQuery.newBuilder();
        b.token(tokenID);

        return Query.newBuilder().tokenGetInfo(b)
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
                        .key(AN_ED25519_KEY)
                        .build());
        return accounts;
    }

    protected Consumer<Response> assertingTokenInfo(
            ) {
        return response -> {
            var info = response.tokenGetInfo();
            var a = response;
        };
    }
}
