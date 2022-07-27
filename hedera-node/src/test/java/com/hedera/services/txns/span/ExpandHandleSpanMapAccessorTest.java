/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.span;

import static com.hedera.services.usage.token.TokenOpsUsageUtils.TOKEN_OPS_USAGE_UTILS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_ACCOUNT_BALANCE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.BDDMockito.given;

import com.hedera.services.ethereum.EthTxData;
import com.hedera.services.ethereum.EthTxSigs;
import com.hedera.services.sigs.order.LinkedRefs;
import com.hedera.services.usage.crypto.CryptoApproveAllowanceMeta;
import com.hedera.services.usage.crypto.CryptoCreateMeta;
import com.hedera.services.usage.crypto.CryptoDeleteAllowanceMeta;
import com.hedera.services.usage.crypto.CryptoUpdateMeta;
import com.hedera.services.usage.util.UtilPrngMeta;
import com.hedera.services.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.math.BigInteger;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpandHandleSpanMapAccessorTest {
    private final Map<String, Object> span = new HashMap<>();

    @Mock private TxnAccessor accessor;

    private ExpandHandleSpanMapAccessor subject;

    @BeforeEach
    void setUp() {
        subject = new ExpandHandleSpanMapAccessor();

        given(accessor.getSpanMap()).willReturn(span);
    }

    @Test
    void managesExpansionAsExpected() {
        final var expansion = new EthTxExpansion(new LinkedRefs(), INSUFFICIENT_ACCOUNT_BALANCE);

        subject.setEthTxExpansion(accessor, expansion);

        assertSame(expansion, subject.getEthTxExpansion(accessor));
    }

    @Test
    void testsForImpliedXfersAsExpected() {
        Assertions.assertDoesNotThrow(() -> subject.getImpliedTransfers(accessor));
    }

    @Test
    void testsForTokenCreateMetaAsExpected() {
        Assertions.assertDoesNotThrow(() -> subject.getTokenCreateMeta(accessor));
    }

    @Test
    void testsForTokenBurnMetaAsExpected() {
        Assertions.assertDoesNotThrow(() -> subject.getTokenBurnMeta(accessor));
    }

    @Test
    void testsForTokenWipeMetaAsExpected() {
        Assertions.assertDoesNotThrow(() -> subject.getTokenWipeMeta(accessor));
    }

    @Test
    void testsForUtilPrngMetaAsExpected() {
        Assertions.assertDoesNotThrow(() -> subject.getUtilPrngMeta(accessor));
    }

    @Test
    void testsForTokenFreezeMetaAsExpected() {
        final var tokenFreezeMeta = TOKEN_OPS_USAGE_UTILS.tokenFreezeUsageFrom();

        subject.setTokenFreezeMeta(accessor, tokenFreezeMeta);

        assertEquals(48, subject.getTokenFreezeMeta(accessor).getBpt());
    }

    @Test
    void testsForTokenUnfreezeMetaAsExpected() {
        final var tokenUnfreezeMeta = TOKEN_OPS_USAGE_UTILS.tokenUnfreezeUsageFrom();

        subject.setTokenUnfreezeMeta(accessor, tokenUnfreezeMeta);

        assertEquals(48, subject.getTokenUnfreezeMeta(accessor).getBpt());
    }

    @Test
    void testsForTokenPauseMetaAsExpected() {
        final var tokenPauseMeta = TOKEN_OPS_USAGE_UTILS.tokenPauseUsageFrom();

        subject.setTokenPauseMeta(accessor, tokenPauseMeta);

        assertEquals(24, subject.getTokenPauseMeta(accessor).getBpt());
    }

    @Test
    void testsForTokenUnpauseMetaAsExpected() {
        final var tokenUnpauseMeta = TOKEN_OPS_USAGE_UTILS.tokenUnpauseUsageFrom();

        subject.setTokenUnpauseMeta(accessor, tokenUnpauseMeta);

        assertEquals(24, subject.getTokenUnpauseMeta(accessor).getBpt());
    }

    @Test
    void testsForCryptoCreateMetaAsExpected() {
        var opMeta =
                new CryptoCreateMeta.Builder()
                        .baseSize(1_234)
                        .lifeTime(1_234_567L)
                        .maxAutomaticAssociations(12)
                        .build();

        subject.setCryptoCreateMeta(accessor, opMeta);

        assertEquals(1_234, subject.getCryptoCreateMeta(accessor).getBaseSize());
    }

    @Test
    void testsForCryptoUpdateMetaAsExpected() {
        final var opMeta =
                new CryptoUpdateMeta.Builder()
                        .keyBytesUsed(123)
                        .msgBytesUsed(1_234)
                        .memoSize(100)
                        .effectiveNow(1_234_000L)
                        .expiry(1_234_567L)
                        .hasProxy(false)
                        .maxAutomaticAssociations(3)
                        .hasMaxAutomaticAssociations(true)
                        .build();

        subject.setCryptoUpdate(accessor, opMeta);

        assertEquals(3, subject.getCryptoUpdateMeta(accessor).getMaxAutomaticAssociations());
    }

    @Test
    void testsForCryptoApproveMetaAsExpected() {
        final var secs = Instant.now().getEpochSecond();
        final var opMeta =
                CryptoApproveAllowanceMeta.newBuilder()
                        .msgBytesUsed(112)
                        .effectiveNow(secs)
                        .build();

        subject.setCryptoApproveMeta(accessor, opMeta);

        assertEquals(112, subject.getCryptoApproveMeta(accessor).getMsgBytesUsed());
        assertEquals(secs, subject.getCryptoApproveMeta(accessor).getEffectiveNow());
    }

    @Test
    void testsForCryptoDeleteMetaAsExpected() {
        final var now = Instant.now().getEpochSecond();
        final var opMeta =
                CryptoDeleteAllowanceMeta.newBuilder().msgBytesUsed(112).effectiveNow(now).build();

        subject.setCryptoDeleteAllowanceMeta(accessor, opMeta);
        assertEquals(112, subject.getCryptoDeleteAllowanceMeta(accessor).getMsgBytesUsed());
        assertEquals(now, subject.getCryptoDeleteAllowanceMeta(accessor).getEffectiveNow());
    }

    @Test
    void testsForEthTxDataMeta() {
        var oneByte = new byte[] {1};
        var ethTxData =
                new EthTxData(
                        oneByte,
                        EthTxData.EthTransactionType.EIP1559,
                        oneByte,
                        1,
                        oneByte,
                        oneByte,
                        oneByte,
                        1,
                        oneByte,
                        BigInteger.ONE,
                        oneByte,
                        oneByte,
                        1,
                        oneByte,
                        oneByte,
                        oneByte);

        subject.setEthTxDataMeta(accessor, ethTxData);
        assertEquals(ethTxData, subject.getEthTxDataMeta(accessor));
    }

    @Test
    void testsForEthTxSigs() {
        var oneByte = new byte[] {1};
        var ethTxSigs = new EthTxSigs(oneByte, oneByte);

        subject.setEthTxSigsMeta(accessor, ethTxSigs);
        assertEquals(ethTxSigs, subject.getEthTxSigsMeta(accessor));
    }

    @Test
    void testsForEthTxBody() {
        var txBody = TransactionBody.newBuilder().build();

        subject.setEthTxBodyMeta(accessor, txBody);
        assertEquals(txBody, subject.getEthTxBodyMeta(accessor));
    }

    @Test
    void testsForUtilPrngMetaBody() {
        final var opMeta = UtilPrngMeta.newBuilder().msgBytesUsed(112).build();

        subject.setUtilPrngMeta(accessor, opMeta);
        assertEquals(112, subject.getUtilPrngMeta(accessor).getMsgBytesUsed());
    }
}
