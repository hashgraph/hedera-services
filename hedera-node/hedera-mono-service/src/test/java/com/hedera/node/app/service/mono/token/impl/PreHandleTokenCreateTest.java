package com.hedera.node.app.service.mono.token.impl;

import com.hedera.node.app.spi.PreHandleContext;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.test.utils.AdapterUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.hedera.node.app.service.mono.Utils.asHederaKey;
import static com.hedera.test.factories.scenarios.TokenCreateScenarios.*;
import static com.hedera.test.utils.AdapterUtils.txnFrom;
import static com.hedera.test.utils.IdUtils.asAccount;
import static org.junit.jupiter.api.Assertions.*;

public class PreHandleTokenCreateTest {

    private final HederaKey adminKey = asHederaKey(TOKEN_ADMIN_KT.asKey()).get();
    private final HederaKey miscKey = asHederaKey(MISC_ACCOUNT_KT.asKey()).get();
    private final HederaKey customPayerKey = asHederaKey(CUSTOM_PAYER_ACCOUNT_KT.asKey()).get();
    private final AccountID payer = asAccount("0.0.3");

    private TokenPreTransactionHandlerImpl subject;

    private PreHandleContext context;

    @BeforeEach
    void setUp() {
        final var now = Instant.now();
        subject =
                new TokenPreTransactionHandlerImpl(
                        AdapterUtils.wellKnownAccountStoreAt(now), context);
    }

    @Test
    void getsTokenCreateAdminKeyOnly() {
        final var meta =
                subject.preHandleCreateToken(txnFrom(TOKEN_CREATE_WITH_ADMIN_ONLY), payer);

        assertTrue(meta.requiredNonPayerKeys().contains(adminKey));
        basicMetaAssertions(meta, 2, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateMissingAdmin() {
        final var meta =
                subject.preHandleCreateToken(txnFrom(TOKEN_CREATE_MISSING_ADMIN), payer);

        assertFalse(meta.requiredNonPayerKeys().contains(adminKey));
        basicMetaAssertions(meta, 1, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateMissingTreasury() {
        final var meta =
                subject.preHandleCreateToken(txnFrom(TOKEN_CREATE_WITH_MISSING_TREASURY), payer);

        basicMetaAssertions(meta, 0, true, ResponseCodeEnum.ACCOUNT_ID_DOES_NOT_EXIST);
    }

    @Test
    void tokenCreateTreasuryAsPayer() {
        final var meta =
                subject.preHandleCreateToken(txnFrom(TOKEN_CREATE_WITH_TREASURY_AS_PAYER), payer);

        basicMetaAssertions(meta, 1, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateTreasuryAsCustomPayer() {
        final var meta =
                subject.preHandleCreateToken(txnFrom(TOKEN_CREATE_WITH_TREASURY_AS_CUSTOM_PAYER), payer);

        assertTrue(meta.requiredNonPayerKeys().contains(customPayerKey));
        basicMetaAssertions(meta, 1, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateWithMissingAutoRenew() {
        final var meta =
                subject.preHandleCreateToken(txnFrom(TOKEN_CREATE_WITH_MISSING_AUTO_RENEW), payer);

        basicMetaAssertions(meta, 1, true, ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT);
    }

    @Test
    void tokenCreateWithAutoRenew() {
        final var meta =
                subject.preHandleCreateToken(txnFrom(TOKEN_CREATE_WITH_AUTO_RENEW), payer);

        assertTrue(meta.requiredNonPayerKeys().contains(miscKey));
        basicMetaAssertions(meta, 2, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateWithAutoRenewAsCustomPayer() {
        final var meta =
                subject.preHandleCreateToken(txnFrom(TOKEN_CREATE_WITH_AUTO_RENEW_AS_CUSTOM_PAYER), payer);

        assertTrue(meta.requiredNonPayerKeys().contains(customPayerKey));
        basicMetaAssertions(meta, 2, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateWithAutoRenewAsPayer() {
        final var meta =
                subject.preHandleCreateToken(txnFrom(TOKEN_CREATE_WITH_AUTO_RENEW_AS_PAYER), payer);

        basicMetaAssertions(meta, 2, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomFeeAndCollectorMissing() {
        final var meta =
                subject.preHandleCreateToken(txnFrom(TOKEN_CREATE_WITH_MISSING_COLLECTOR), payer);

        basicMetaAssertions(meta, 1, true, ResponseCodeEnum.RECEIVER_SIG_REQUIRED);
    }

    @Test
    void tokenCreateCustomFixedFeeAndCollectorSigReq() {
        final var meta =
                subject.preHandleCreateToken(txnFrom(TOKEN_CREATE_WITH_FIXED_FEE_COLLECTOR_SIG_REQ), payer);

        basicMetaAssertions(meta, 2, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomFixedFeeAndCollectorSigReqAndAsPayer() {
        final var meta =
                subject.preHandleCreateToken(txnFrom(TOKEN_CREATE_WITH_FIXED_FEE_COLLECTOR_SIG_REQ_AND_AS_PAYER), payer);

        basicMetaAssertions(meta, 1, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomFixedFeeNoCollectorSigReq() {
        final var meta =
                subject.preHandleCreateToken(txnFrom(TOKEN_CREATE_WITH_FIXED_FEE_NO_COLLECTOR_SIG_REQ), payer);

        basicMetaAssertions(meta, 1, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomFixedFeeNoCollectorSigReqButDenomWildcard() {
        final var meta =
                subject.preHandleCreateToken(
                        txnFrom(TOKEN_CREATE_WITH_FIXED_FEE_NO_COLLECTOR_SIG_REQ_BUT_USING_WILDCARD_DENOM), payer);

        basicMetaAssertions(meta, 2, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomFractionalFeeNoCollectorSigReq() {
        final var meta =
                subject.preHandleCreateToken(
                        txnFrom(TOKEN_CREATE_WITH_FRACTIONAL_FEE_COLLECTOR_NO_SIG_REQ), payer);

        basicMetaAssertions(meta, 2, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomRoyaltyFeeFallbackNoWildcardButSigReq() {
        final var meta =
                subject.preHandleCreateToken(
                        txnFrom(TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_FALLBACK_NO_WILDCARD_BUT_SIG_REQ), payer);

        basicMetaAssertions(meta, 1, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomRoyaltyFeeFallbackWildcardNoSigReq() {
        final var meta =
                subject.preHandleCreateToken(
                        txnFrom(TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_FALLBACK_WILDCARD_AND_NO_SIG_REQ), payer);

        basicMetaAssertions(meta, 2, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomRoyaltyFeeNoFallbackAndNoCollectorSigReq() {
        final var meta =
                subject.preHandleCreateToken(
                        txnFrom(TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_NO_SIG_REQ_NO_FALLBACK), payer);

        basicMetaAssertions(meta, 1, false, ResponseCodeEnum.OK);
    }

    @Test
    void tokenCreateCustomRoyaltyFeeNoFallbackButSigReq() {
        final var meta =
                subject.preHandleCreateToken(
                        txnFrom(TOKEN_CREATE_WITH_ROYALTY_FEE_COLLECTOR_SIG_REQ_NO_FALLBACK), payer);

        basicMetaAssertions(meta, 2, false, ResponseCodeEnum.OK);
    }

    private void basicMetaAssertions(
            final TransactionMetadata meta,
            final int keysSize,
            final boolean failed,
            final ResponseCodeEnum failureStatus) {
        assertEquals(keysSize, meta.requiredNonPayerKeys().size());
        assertEquals(failed, meta.failed());
        assertEquals(failureStatus, meta.status());
    }
}
