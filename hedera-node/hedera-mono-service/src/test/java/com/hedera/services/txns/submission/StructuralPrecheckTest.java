/*
 * Copyright (C) 2021-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.txns.submission;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_OVERSIZE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSACTION_TOO_MANY_LAYERS;
import static java.util.stream.Collectors.joining;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import com.google.protobuf.ByteString;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.domain.process.TxnValidityAndFeeReq;
import com.hedera.services.context.primitives.SignedStateViewFactory;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.stats.HapiOpCounters;
import com.hedera.services.stats.MiscRunningAvgs;
import com.hedera.services.utils.accessors.AccessorFactory;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.SignatureList;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.swirlds.common.metrics.Counter;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.system.Platform;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.IntStream;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StructuralPrecheckTest {
    private static final int pretendSizeLimit = 1_000;
    private static final int pretendMaxMessageDepth = 42;
    private StructuralPrecheck subject;

    private TransactionContext txnCtx = mock(TransactionContext.class);
    private Function<HederaFunctionality, String> statNameFn = HederaFunctionality::toString;
    private MiscRunningAvgs runningAvgs = mock(MiscRunningAvgs.class);

    private HapiOpCounters counters = new HapiOpCounters(runningAvgs, txnCtx, statNameFn);

    @Mock private Counter counter;
    @Mock private Platform platform;
    @Mock private Metrics metrics;
    private SignedStateViewFactory viewFactory = mock(SignedStateViewFactory.class);
    private AccessorFactory accessorFactory = mock(AccessorFactory.class);

    private SignedTxnAccessor accessor = mock(SignedTxnAccessor.class);
    private Transaction txn;

    @BeforeEach
    void setUp() {
        subject =
                new StructuralPrecheck(
                        pretendSizeLimit,
                        pretendMaxMessageDepth,
                        counters,
                        viewFactory,
                        accessorFactory);
    }

    @Test
    void mustHaveBodyBytes() throws InvalidProtocolBufferException {
        txn = Transaction.getDefaultInstance();
        willCallRealMethod().given(accessorFactory).constructSpecializedAccessor(txn);
        given(accessor.getTxn()).willReturn(txn.getBody());

        final var assess = subject.assess(txn);

        assertExpectedFail(INVALID_TRANSACTION_BODY, assess);
    }

    @Test
    void cantMixSignedBytesWithBodyBytes() throws InvalidProtocolBufferException {
        withVerifiableCounters();
        txn =
                Transaction.newBuilder()
                        .setSignedTransactionBytes(ByteString.copyFromUtf8("w/e"))
                        .setBodyBytes(ByteString.copyFromUtf8("doesn't matter"))
                        .build();
        willCallRealMethod().given(accessorFactory).constructSpecializedAccessor(txn);
        given(accessor.getTxn()).willReturn(txn.getBody());

        final var assess = subject.assess(txn);

        assertExpectedFail(INVALID_TRANSACTION, assess);
        verify(counter).increment();
    }

    @Test
    void cantMixSignedBytesWithSigMap() throws InvalidProtocolBufferException {
        withVerifiableCounters();
        txn =
                Transaction.newBuilder()
                        .setSignedTransactionBytes(ByteString.copyFromUtf8("w/e"))
                        .setSigMap(SignatureMap.getDefaultInstance())
                        .build();
        willCallRealMethod().given(accessorFactory).constructSpecializedAccessor(txn);
        given(accessor.getTxn()).willReturn(txn.getBody());

        final var assess = subject.assess(txn);

        assertExpectedFail(INVALID_TRANSACTION, assess);
        verify(counter).increment();
    }

    @Test
    void cantBeOversize() throws InvalidProtocolBufferException {
        withVerifiableCounters();
        txn =
                Transaction.newBuilder()
                        .setSignedTransactionBytes(
                                ByteString.copyFromUtf8(
                                        IntStream.range(0, pretendSizeLimit)
                                                .mapToObj(i -> "A")
                                                .collect(joining())))
                        .build();
        willCallRealMethod().given(accessorFactory).constructSpecializedAccessor(txn);
        given(accessor.getTxn()).willReturn(txn.getBody());

        final var assess = subject.assess(txn);

        assertExpectedFail(TRANSACTION_OVERSIZE, assess);
        verifyNoInteractions(counter);
    }

    @Test
    void mustParseViaAccessor() throws InvalidProtocolBufferException {
        withVerifiableCounters();
        txn =
                Transaction.newBuilder()
                        .setSignedTransactionBytes(ByteString.copyFromUtf8("NONSENSE"))
                        .build();
        willCallRealMethod().given(accessorFactory).constructSpecializedAccessor(txn);
        given(accessor.getTxn()).willReturn(txn.getBody());

        final var assess = subject.assess(txn);

        assertExpectedFail(INVALID_TRANSACTION_BODY, assess);
        verifyNoInteractions(counter);
    }

    @Test
    void cantBeUndulyNested() throws InvalidProtocolBufferException {
        withVerifiableCounters();
        final var weirdlyNestedKey = TxnUtils.nestKeys(Key.newBuilder(), pretendMaxMessageDepth);
        final var hostTxn =
                TransactionBody.newBuilder()
                        .setCryptoCreateAccount(
                                CryptoCreateTransactionBody.newBuilder().setKey(weirdlyNestedKey));
        final var signedTxn =
                Transaction.newBuilder().setBodyBytes(hostTxn.build().toByteString()).build();
        willCallRealMethod().given(accessorFactory).constructSpecializedAccessor(signedTxn);
        given(accessor.getTxn()).willReturn(hostTxn.build());
        final var assess = subject.assess(signedTxn);

        assertExpectedFail(TRANSACTION_TOO_MANY_LAYERS, assess);
        verify(counter).increment();
    }

    @Test
    void cantOmitAFunction() throws InvalidProtocolBufferException {
        withVerifiableCounters();
        final var hostTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(
                                TransactionID.newBuilder()
                                        .setAccountID(IdUtils.asAccount("0.0.2")));
        final var signedTxn =
                Transaction.newBuilder().setBodyBytes(hostTxn.build().toByteString()).build();

        willCallRealMethod().given(accessorFactory).constructSpecializedAccessor(signedTxn);
        given(accessor.getTxn()).willReturn(hostTxn.build());

        final var assess = subject.assess(signedTxn);

        assertExpectedFail(INVALID_TRANSACTION_BODY, assess);
        verify(counter).increment();
    }

    @Test
    void canBeOkAndSetsStateView() throws InvalidProtocolBufferException {
        withVerifiableCounters();
        final var reasonablyNestedKey = TxnUtils.nestKeys(Key.newBuilder(), 2);
        final var hostTxn =
                TransactionBody.newBuilder()
                        .setCryptoCreateAccount(
                                CryptoCreateTransactionBody.newBuilder()
                                        .setKey(reasonablyNestedKey));
        final var signedTxn =
                Transaction.newBuilder().setBodyBytes(hostTxn.build().toByteString()).build();
        final var view = mock(StateView.class);

        willCallRealMethod().given(accessorFactory).constructSpecializedAccessor(signedTxn);
        given(accessor.getTxn()).willReturn(hostTxn.build());
        given(viewFactory.latestSignedStateView()).willReturn(Optional.of(view));

        final var assess = subject.assess(signedTxn);

        assertEquals(OK, assess.getLeft().getValidity());
        assertNotNull(assess.getRight());
        assertEquals(view, assess.getRight().getStateView());
        assertEquals(HederaFunctionality.CryptoCreate, assess.getRight().getFunction());
        verify(counter).increment();
    }

    @Test
    void computesExpectedDepth() {
        final var weirdlyNestedKey =
                TxnUtils.nestKeys(Key.newBuilder(), pretendMaxMessageDepth).build();
        final var expectedDepth = verboseCalc(weirdlyNestedKey);

        final var actualDepth = subject.protoDepthOf(weirdlyNestedKey);

        assertEquals(expectedDepth, actualDepth);
    }

    @Test
    void validateCounterForDeprecatedTransactions() throws InvalidProtocolBufferException {
        withVerifiableCounters();
        final var hostTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(
                                TransactionID.newBuilder()
                                        .setAccountID(IdUtils.asAccount("0.0.2")));
        var signedTxn =
                Transaction.newBuilder().setBodyBytes(hostTxn.build().toByteString()).build();
        willCallRealMethod().given(accessorFactory).constructSpecializedAccessor(signedTxn);
        given(accessor.getTxn()).willReturn(hostTxn.build());

        subject.assess(signedTxn);

        signedTxn = Transaction.newBuilder().setSigMap(SignatureMap.newBuilder().build()).build();
        subject.assess(signedTxn);

        signedTxn = Transaction.newBuilder().setBody(TransactionBody.newBuilder().build()).build();
        subject.assess(signedTxn);

        signedTxn = Transaction.newBuilder().setSigs(SignatureList.newBuilder().build()).build();
        subject.assess(signedTxn);
        verify(counter, times(4)).increment();
    }

    @Test
    void txnWithNoDeprecatedFieldsDoesntIncrement() throws InvalidProtocolBufferException {
        withVerifiableCounters();
        final var hostTxn =
                TransactionBody.newBuilder()
                        .setTransactionID(
                                TransactionID.newBuilder()
                                        .setAccountID(IdUtils.asAccount("0.0.2")));
        var signedTxn =
                Transaction.newBuilder()
                        .setSignedTransactionBytes(hostTxn.build().toByteString())
                        .build();
        willCallRealMethod().given(accessorFactory).constructSpecializedAccessor(signedTxn);
        given(accessor.getTxn()).willReturn(hostTxn.build());

        subject.assess(signedTxn);
        verifyNoInteractions(counter);
    }

    private int verboseCalc(final GeneratedMessageV3 msg) {
        final var fields = msg.getAllFields();
        int depth = 0;
        for (final var field : fields.values()) {
            if (field instanceof GeneratedMessageV3) {
                GeneratedMessageV3 fieldMessage = (GeneratedMessageV3) field;
                depth = Math.max(depth, verboseCalc(fieldMessage) + 1);
            } else if (field instanceof List) {
                for (final Object ele : (List) field) {
                    if (ele instanceof GeneratedMessageV3) {
                        depth = Math.max(depth, verboseCalc((GeneratedMessageV3) ele) + 1);
                    }
                }
            }
        }
        return depth;
    }

    private void assertExpectedFail(
            final ResponseCodeEnum error,
            final Pair<TxnValidityAndFeeReq, SignedTxnAccessor> resp) {
        assertEquals(error, resp.getLeft().getValidity());
        assertNull(resp.getRight());
    }

    private void withVerifiableCounters() {
        given(platform.getMetrics()).willReturn(metrics);
        given(metrics.getOrCreate(any())).willReturn(counter);
        counters.registerWith(platform);
    }
}
