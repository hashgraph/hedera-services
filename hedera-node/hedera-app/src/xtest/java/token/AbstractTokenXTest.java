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
import com.hedera.pbj.runtime.io.buffer.Bytes;
import common.AbstractXTest;
import common.BaseScaffoldingComponent;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public abstract class AbstractTokenXTest extends AbstractXTest {
    protected static final AccountID DEFAULT_PAYER_ID = AccountID.newBuilder().accountNum(2L).build();
    protected TokenScaffoldingComponent component;

    @BeforeEach
    void setUp() {
        component = DaggerTokenScaffoldingComponent.factory().create(metrics);
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
            final List<TokenTransferList> newTransfers = new ArrayList<>(b.build().tokenTransfersOrThrow());
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
            final List<TokenTransferList> newTransfers = new ArrayList<>(b.build().tokenTransfersOrThrow());
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
                        .exchangeValueFraction(Fraction.newBuilder()
                                .numerator(numerator)
                                .denominator(denominator))
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
                .cryptoTransfer(b).build();
    }

    protected final TransactionBody nftMint(@NonNull final Bytes metadata, @NonNull final String token) {
        return nftMint(metadata, idOfNamedToken(token));
    }

    protected final TransactionBody nftMint(@NonNull final Bytes metadata, @NonNull final TokenID tokenID) {
        final var b = TokenMintTransactionBody.newBuilder();
        b.metadata(metadata).token(tokenID);
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(DEFAULT_PAYER_ID))
                .tokenMint(b).build();
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        final var accounts = super.initialAccounts();
        accounts.put(DEFAULT_PAYER_ID, Account.newBuilder()
                .accountId(DEFAULT_PAYER_ID)
                .tinybarBalance(Long.MAX_VALUE / 2)
                .build());
        return accounts;
    }
}
