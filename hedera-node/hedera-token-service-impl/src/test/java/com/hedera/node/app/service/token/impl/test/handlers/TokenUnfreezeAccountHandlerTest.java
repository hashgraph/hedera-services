package com.hedera.node.app.service.token.impl.test.handlers;

import com.hedera.node.app.service.token.impl.ReadableTokenStore;
import com.hedera.node.app.service.token.impl.handlers.TokenUnfreezeAccountHandler;
import com.hedera.node.app.service.token.impl.test.util.SigReqAdapterUtils;
import com.hedera.node.app.spi.AccountKeyLookup;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.hedera.node.app.service.token.impl.test.handlers.AdapterUtils.txnFrom;
import static com.hedera.node.app.service.token.impl.test.util.MetaAssertion.basicMetaAssertions;
import static com.hedera.test.factories.scenarios.TokenUnfreezeScenarios.UNFREEZE_WITH_INVALID_FREEZE_KEY;
import static com.hedera.test.factories.scenarios.TokenUnfreezeScenarios.UNFREEZE_WITH_INVALID_TOKEN;
import static com.hedera.test.factories.scenarios.TokenUnfreezeScenarios.UNFREEZE_WITH_MISSING_FREEZE_TOKEN;
import static com.hedera.test.factories.scenarios.TokenUnfreezeScenarios.VALID_UNFREEZE_WITH_EXTANT_TOKEN;
import static com.hedera.test.factories.scenarios.TxnHandlingScenario.TOKEN_FREEZE_KT;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;
import static com.hedera.test.utils.KeyUtils.sanityRestored;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.junit.jupiter.api.Assertions.assertEquals;

class TokenUnfreezeAccountHandlerTest {
    private AccountKeyLookup accountStore;
    private ReadableTokenStore tokenStore;
    private TokenUnfreezeAccountHandler subject;

    @BeforeEach
    void setUp() {
        accountStore = AdapterUtils.wellKnownKeyLookupAt();
        tokenStore = SigReqAdapterUtils.wellKnownTokenStoreAt();
        subject = new TokenUnfreezeAccountHandler();
    }

    @Test
    void tokenUnfreezeWithExtantFreezable() {
        final var txn = txnFrom(VALID_UNFREEZE_WITH_EXTANT_TOKEN);

        final var meta =
                subject.preHandle(txn, txn.getTransactionID().getAccountID(), tokenStore, accountStore);

        assertEquals(sanityRestored(meta.payerKey()), DEFAULT_PAYER_KT.asKey());
        assertThat(
                sanityRestored(meta.requiredNonPayerKeys()),
                contains(TOKEN_FREEZE_KT.asKey()));
        basicMetaAssertions(meta, 1, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenUnfreezeMissingToken() {
        final var txn = txnFrom(UNFREEZE_WITH_MISSING_FREEZE_TOKEN);

        final var meta =
                subject.preHandle(txn, txn.getTransactionID().getAccountID(), tokenStore, accountStore);

        assertEquals(sanityRestored(meta.payerKey()), DEFAULT_PAYER_KT.asKey());
        basicMetaAssertions(meta, 0, true, ResponseCodeEnum.INVALID_TOKEN_ID);
    }

    @Test
    void tokenUnfreezeWithInvalidToken() {
        final var txn = txnFrom(UNFREEZE_WITH_INVALID_TOKEN);

        final var meta =
                subject.preHandle(txn, txn.getTransactionID().getAccountID(), tokenStore, accountStore);

        assertEquals(sanityRestored(meta.payerKey()), DEFAULT_PAYER_KT.asKey());
        basicMetaAssertions(meta, 0, true, ResponseCodeEnum.INVALID_TOKEN_ID);
    }

    @Test
    void tokenUnfreezeWithInvalidFreezeKey() {
        final var txn = txnFrom(UNFREEZE_WITH_INVALID_FREEZE_KEY);

        final var meta =
                subject.preHandle(txn, txn.getTransactionID().getAccountID(), tokenStore, accountStore);

        assertEquals(sanityRestored(meta.payerKey()), DEFAULT_PAYER_KT.asKey());
        basicMetaAssertions(meta, 0, true, ResponseCodeEnum.TOKEN_HAS_NO_FREEZE_KEY);
    }

    @Test
    void handleFunctionalityTest() {
        final var notImplemented = "Not implemented";
        try {
            subject.handle(null);
        } catch (final UnsupportedOperationException e) {
            assertEquals(e.getMessage(), notImplemented);
        }
    }
}
