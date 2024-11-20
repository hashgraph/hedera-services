/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.tss.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.cryptography.bls.BlsPrivateKey;
import com.hedera.cryptography.bls.BlsSignature;
import com.hedera.cryptography.bls.SignatureSchema;
import com.hedera.cryptography.tss.api.TssParticipantDirectory;
import com.hedera.cryptography.tss.api.TssPrivateShare;
import com.hedera.cryptography.tss.api.TssPublicShare;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssShareSignatureTransactionBody;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.tss.TssBaseServiceImpl;
import com.hedera.node.app.tss.TssKeysAccessor;
import com.hedera.node.app.tss.TssMetrics;
import com.hedera.node.app.tss.api.FakeFieldElement;
import com.hedera.node.app.tss.api.FakeGroupElement;
import com.hedera.node.app.tss.api.TssLibrary;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.math.BigInteger;
import java.time.Instant;
import java.time.InstantSource;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class TssShareSignatureHandlerTest {
    @Mock
    private TssLibrary tssLibrary;

    @Mock
    private TssKeysAccessor rosterKeyMaterialAccessor;

    @Mock
    private InstantSource instantSource;

    @Mock
    private TssBaseServiceImpl tssBaseService;

    @Mock
    private PreHandleContext context;

    @Mock
    private TssMetrics tssMetrics;

    private TssShareSignatureHandler handler;

    private static final SignatureSchema SIGNATURE_SCHEMA = SignatureSchema.create(new byte[] {1});
    public static final BlsPrivateKey PRIVATE_KEY =
            new BlsPrivateKey(new FakeFieldElement(BigInteger.valueOf(42L)), SIGNATURE_SCHEMA);
    private static final BlsSignature SIGNATURE =
            new BlsSignature(new FakeGroupElement(BigInteger.valueOf(42L)), SIGNATURE_SCHEMA);
    public static final TssKeysAccessor.TssKeys TSS_KEYS = new TssKeysAccessor.TssKeys(
            List.of(new TssPrivateShare(0, PRIVATE_KEY)),
            List.of(new TssPublicShare(0, PRIVATE_KEY.createPublicKey())),
            Bytes.EMPTY,
            TssParticipantDirectory.createBuilder()
                    .withParticipant(0, 1, PRIVATE_KEY.createPublicKey())
                    .build(),
            1);

    @BeforeEach
    void setUp() {
        given(rosterKeyMaterialAccessor.accessTssKeys()).willReturn(TSS_KEYS);
        given(instantSource.instant()).willReturn(Instant.ofEpochSecond(1_234_567L));
        handler = new TssShareSignatureHandler(
                tssLibrary, instantSource, rosterKeyMaterialAccessor, tssBaseService, tssMetrics);
    }

    @Test
    void testPreHandleValidSignatureAndThresholdMet() throws PreCheckException {
        when(context.body()).thenReturn(mockTransactionBody());
        when(tssLibrary.verifySignature(any(), any(), any())).thenReturn(true);
        when(tssLibrary.aggregateSignatures(anyList())).thenReturn(SIGNATURE);

        handler.preHandle(context);
        verify(tssBaseService).notifySignature(any(), any());
        assertEquals(
                1,
                handler.getSignatures()
                        .get(Bytes.wrap("message"))
                        .get(Bytes.wrap("roster"))
                        .size());
        assertEquals(1, handler.getRequests().size());
    }

    @Test
    void testPreHandleSignatureAlreadyPresent() throws PreCheckException {
        when(context.body()).thenReturn(mockTransactionBody());
        when(tssLibrary.verifySignature(any(), any(), any())).thenReturn(true);
        when(tssLibrary.aggregateSignatures(anyList())).thenReturn(SIGNATURE);

        handler.preHandle(context);
        verify(tssBaseService).notifySignature(any(), any());
        assertEquals(
                1,
                handler.getSignatures()
                        .get(Bytes.wrap("message"))
                        .get(Bytes.wrap("roster"))
                        .size());
        assertEquals(1, handler.getRequests().size());

        // Signature is already present
        handler.preHandle(context);
        verify(tssBaseService, times(1)).notifySignature(any(), any());
        assertEquals(
                1,
                handler.getSignatures()
                        .get(Bytes.wrap("message"))
                        .get(Bytes.wrap("roster"))
                        .size());
        assertEquals(1, handler.getRequests().size());
    }

    private TransactionBody mockTransactionBody() {
        return TransactionBody.newBuilder()
                .tssShareSignature(TssShareSignatureTransactionBody.newBuilder()
                        .rosterHash(Bytes.wrap("roster"))
                        .messageHash(Bytes.wrap("message"))
                        .shareSignature(Bytes.wrap("signature"))
                        .build())
                .build();
    }
}
