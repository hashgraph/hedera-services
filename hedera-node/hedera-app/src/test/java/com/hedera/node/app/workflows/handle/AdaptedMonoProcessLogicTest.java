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

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.google.protobuf.ByteString;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.node.app.service.mono.legacy.core.jproto.JEd25519Key;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.logic.StandardProcessLogic;
import com.hedera.node.app.service.mono.utils.accessors.SwirldsTxnAccessor;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.workflows.prehandle.PreHandleResult;
import com.hederahashgraph.api.proto.java.Transaction;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.system.transaction.internal.ConsensusTransactionImpl;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdaptedMonoProcessLogicTest {
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
        final var cryptoSigs = List.of(signature);
        final var meta = new PreHandleResult(
                null, null, null, ResponseCodeEnum.OK, PAYER_KEY, OTHER_PARTY_KEYS, cryptoSigs, null);

        given(platformTxn.getMetadata()).willReturn(meta);
        given(platformTxn.getContents()).willReturn(noopTxn.toByteArray());

        subject.incorporateConsensusTxn(platformTxn, 1L);

        verify(platformTxn).setMetadata(captor.capture());
        final var accessor = captor.getValue();
        assertEquals(OK, accessor.getExpandedSigStatus());
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
        final var meta = new PreHandleResult(
                null, null, null, ResponseCodeEnum.INVALID_ACCOUNT_ID, PAYER_KEY, OTHER_PARTY_KEYS, cryptoSigs, null);

        given(platformTxn.getMetadata()).willReturn(meta);
        given(platformTxn.getContents()).willReturn(noopTxn.toByteArray());

        subject.incorporateConsensusTxn(platformTxn, 1L);

        verify(platformTxn).setMetadata(captor.capture());
        final var accessor = captor.getValue();
        assertEquals(INVALID_ACCOUNT_ID, accessor.getExpandedSigStatus());
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
                null, null, null, ResponseCodeEnum.INVALID_ACCOUNT_ID, null, OTHER_PARTY_KEYS, cryptoSigs, null);

        given(platformTxn.getMetadata()).willReturn(meta);
        given(platformTxn.getContents()).willReturn(noopTxn.toByteArray());

        subject.incorporateConsensusTxn(platformTxn, 1L);

        verify(platformTxn).setMetadata(captor.capture());
        final var accessor = captor.getValue();
        assertEquals(INVALID_ACCOUNT_ID, accessor.getExpandedSigStatus());
        assertNotNull(accessor.getLinkedRefs());
        final var sigMeta = accessor.getSigMeta();
        assertFalse(sigMeta.couldRationalizePayer());
        assertFalse(sigMeta.couldRationalizeOthers());
    }

    @Test
    void translatesUnparseableContentsAsISE() {
        final ArgumentCaptor<SwirldsTxnAccessor> captor = ArgumentCaptor.forClass(SwirldsTxnAccessor.class);

        final var nonsenseTxn = Transaction.newBuilder()
                .setSignedTransactionBytes(ByteString.copyFrom("NONSENSE".getBytes()))
                .build();
        final var cryptoSigs = List.of(signature);
        final var meta = new PreHandleResult(
                null, null, null, ResponseCodeEnum.INVALID_ACCOUNT_ID, null, OTHER_PARTY_KEYS, cryptoSigs, null);

        given(platformTxn.getMetadata()).willReturn(meta);
        given(platformTxn.getContents()).willReturn(nonsenseTxn.toByteArray());

        assertThrows(IllegalStateException.class, () -> subject.incorporateConsensusTxn(platformTxn, 1L));
    }

    private static final JKey PAYER_KEY = new JEd25519Key("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes());
    private static final List<HederaKey> OTHER_PARTY_KEYS = List.of(
            new JEd25519Key("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb".getBytes()),
            new JEd25519Key("cccccccccccccccccccccccccccccccc".getBytes()));
}
