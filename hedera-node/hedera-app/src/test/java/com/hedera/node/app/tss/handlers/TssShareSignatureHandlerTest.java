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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.services.auxiliary.tss.TssShareSignatureTransactionBody;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.tss.TssBaseServiceImpl;
import com.hedera.node.app.tss.TssKeyMaterialAccessor;
import com.hedera.node.app.tss.api.TssLibrary;
import com.hedera.node.app.tss.api.TssParticipantDirectory;
import com.hedera.node.app.tss.pairings.FakeFieldElement;
import com.hedera.node.app.tss.pairings.PairingPrivateKey;
import com.hedera.node.app.tss.pairings.SignatureSchema;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.math.BigInteger;
import java.time.Instant;
import java.time.InstantSource;
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
    private TssKeyMaterialAccessor rosterKeyMaterialAccessor;

    @Mock
    private InstantSource instantSource;

    @Mock
    private TssBaseServiceImpl tssBaseService;

    @Mock
    private PreHandleContext context;

    private TssShareSignatureHandler handler;

    private static final SignatureSchema SIGNATURE_SCHEMA = SignatureSchema.create(new byte[] {1});
    private static final PairingPrivateKey PRIVATE_KEY =
            new PairingPrivateKey(new FakeFieldElement(BigInteger.valueOf(42L)), SIGNATURE_SCHEMA);

    @BeforeEach
    void setUp() {
        given(rosterKeyMaterialAccessor.activeRosterParticipantDirectory())
                .willReturn(TssParticipantDirectory.createBuilder()
                        .withParticipant(0, 1, PRIVATE_KEY.createPublicKey())
                        .withSelf(0, PRIVATE_KEY)
                        .build(SIGNATURE_SCHEMA));
        given(instantSource.instant()).willReturn(Instant.ofEpochSecond(1_234_567L));
        handler = new TssShareSignatureHandler(tssLibrary, instantSource, rosterKeyMaterialAccessor, tssBaseService);
    }

    @Test
    void testPreHandleValidSignatureAndThresholdMet() throws PreCheckException {
        when(context.body()).thenReturn(mockTransactionBody());
        when(tssLibrary.verifySignature(any(), any(), any())).thenReturn(true);

        handler.preHandle(context);
        verify(tssBaseService).notifySignature(any(), any());
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
