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

package com.hedera.node.app.workflows.handle;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static java.util.concurrent.CompletableFuture.completedFuture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.node.app.AppTestBase;
import com.hedera.node.app.service.mono.legacy.core.jproto.JEd25519Key;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.logic.StandardProcessLogic;
import com.hedera.node.app.service.mono.utils.accessors.SwirldsTxnAccessor;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.hedera.node.app.signature.hapi.SignatureVerificationImpl;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.hedera.node.app.workflows.prehandle.PreHandleResult.Status;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdaptedMonoProcessLogicTest extends AppTestBase {
    @Mock
    private StandardProcessLogic monoProcessLogic;

    @Mock
    private ConsensusTransactionImpl platformTxn;

    @Mock
    private TxnAccessor accessor;

    @Mock
    private TransactionSignature signature;

    private AdaptedMonoProcessLogic subject;

    @BeforeEach
    void setUp() {
        subject = new AdaptedMonoProcessLogic(monoProcessLogic);
    }

    @Test
    void passesThroughNonPreHandleResult() {
        given(platformTxn.getMetadata()).willReturn(accessor);

        subject.incorporateConsensusTxn(platformTxn, 1L);

        verify(monoProcessLogic).incorporateConsensusTxn(platformTxn, 1L);
    }

    @Test
    void adaptsPreHandleResultAsPayerAndOthersIfOK() {
        final ArgumentCaptor<SwirldsTxnAccessor> captor = ArgumentCaptor.forClass(SwirldsTxnAccessor.class);

        final var noopTxn = Transaction.newBuilder().build();
        final var meta = new PreHandleResult(
                // payer, status, responseCode, txInfo, payerFuture, nonPayerFutures, nonPayerHollowFutures, innerResult
                null, Status.SO_FAR_SO_GOOD, OK, null, verificationFuture(PAYER_KEY_PBJ), Map.of(), Map.of(), null);

        given(platformTxn.getMetadata()).willReturn(meta);
        given(platformTxn.getContents()).willReturn(asByteArray(noopTxn));

        subject.incorporateConsensusTxn(platformTxn, 1L);

        verify(platformTxn).setMetadata(captor.capture());
        final var accessor = captor.getValue();
        assertEquals(com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK, accessor.getExpandedSigStatus());
        assertNotNull(accessor.getLinkedRefs());
        final var sigMeta = accessor.getSigMeta();
        assertTrue(sigMeta.couldRationalizePayer());
        assertTrue(sigMeta.couldRationalizeOthers());
    }

    @Test
    void adaptsTransactionPayerOnlyIfNotOK() {
        final ArgumentCaptor<SwirldsTxnAccessor> captor = ArgumentCaptor.forClass(SwirldsTxnAccessor.class);

        final var noopTxn = Transaction.newBuilder().build();
        final var cryptoSigs = List.of(signature);
        // TODO OTHER_PARTY_KEYS? cryptoSigs?
        final var meta = new PreHandleResult(
                null,
                Status.NODE_DUE_DILIGENCE_FAILURE,
                INVALID_ACCOUNT_ID,
                null,
                verificationFuture(PAYER_KEY_PBJ),
                Map.of(),
                Map.of(),
                null);

        given(platformTxn.getMetadata()).willReturn(meta);
        given(platformTxn.getContents()).willReturn(asByteArray(noopTxn));

        subject.incorporateConsensusTxn(platformTxn, 1L);

        verify(platformTxn).setMetadata(captor.capture());
        final var accessor = captor.getValue();
        assertEquals(
                com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID,
                accessor.getExpandedSigStatus());
        assertNotNull(accessor.getLinkedRefs());
        final var sigMeta = accessor.getSigMeta();
        assertTrue(sigMeta.couldRationalizePayer());
        assertFalse(sigMeta.couldRationalizeOthers());
    }

    @Test
    void adaptsTransactionNonAvailableIfNullPayerKey() {
        final ArgumentCaptor<SwirldsTxnAccessor> captor = ArgumentCaptor.forClass(SwirldsTxnAccessor.class);

        final var noopTxn = Transaction.newBuilder().build();
        final var cryptoSigs = List.of(signature);
        final var meta = new PreHandleResult(
                null,
                Status.NODE_DUE_DILIGENCE_FAILURE,
                INVALID_ACCOUNT_ID,
                null,
                verificationFuture(null),
                Map.of(),
                Map.of(),
                null);

        given(platformTxn.getMetadata()).willReturn(meta);
        given(platformTxn.getContents()).willReturn(asByteArray(noopTxn));

        subject.incorporateConsensusTxn(platformTxn, 1L);

        verify(platformTxn).setMetadata(captor.capture());
        final var accessor = captor.getValue();
        assertEquals(
                com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID,
                accessor.getExpandedSigStatus());
        assertNotNull(accessor.getLinkedRefs());
        final var sigMeta = accessor.getSigMeta();
        assertFalse(sigMeta.couldRationalizePayer());
        assertFalse(sigMeta.couldRationalizeOthers());
    }

    @Test
    void translatesUnparseableContentsAsISE() {
        final ArgumentCaptor<SwirldsTxnAccessor> captor = ArgumentCaptor.forClass(SwirldsTxnAccessor.class);

        final var nonsenseTxn = Transaction.newBuilder()
                .signedTransactionBytes(Bytes.wrap("NONSENSE"))
                .build();
        final var cryptoSigs = List.of(signature);
        final var meta = new PreHandleResult(
                null,
                Status.NODE_DUE_DILIGENCE_FAILURE,
                INVALID_ACCOUNT_ID,
                null,
                verificationFuture(null),
                Map.of(),
                Map.of(),
                null);

        given(platformTxn.getMetadata()).willReturn(meta);
        given(platformTxn.getContents()).willReturn(asByteArray(nonsenseTxn));

        assertThrows(IllegalStateException.class, () -> subject.incorporateConsensusTxn(platformTxn, 1L));
    }

    private static final JKey PAYER_KEY = new JEd25519Key("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes());
    private static final Key PAYER_KEY_PBJ = Key.newBuilder()
            .ed25519(Bytes.wrap("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
            .build();
    private static final Set<HederaKey> OTHER_PARTY_KEYS = Set.of(
            new JEd25519Key("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb".getBytes()),
            new JEd25519Key("cccccccccccccccccccccccccccccccc".getBytes()));

    private static Future<SignatureVerification> verificationFuture(Key payerKey) {
        return completedFuture(new SignatureVerificationImpl(payerKey, null, List.of(), true));
    }
}
