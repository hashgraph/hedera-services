/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.common;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Parser;
import com.hedera.node.app.service.mono.context.properties.NodeLocalProperties;
import com.hedera.node.app.service.mono.context.properties.Profile;
import com.hedera.node.app.service.mono.records.RecordCache;
import com.hedera.node.app.service.mono.stats.MiscSpeedometers;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.workflows.ingest.SubmissionManager;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.UncheckedSubmitBody;
import com.swirlds.common.system.Platform;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SubmissionManagerTest {

    @Mock
    private Platform platform;

    @Mock
    private RecordCache recordCache;

    @Mock
    private NodeLocalProperties nodeLocalProperties;

    @Mock
    private MiscSpeedometers speedometers;

    @Mock
    private Parser<TransactionBody> parser;

    private byte[] bytes;

    private SubmissionManager submissionManager;

    @BeforeEach
    void setup() {
        bytes = new byte[] {1, 2, 3};
        submissionManager = new SubmissionManager(platform, recordCache, nodeLocalProperties, speedometers);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testConstructorWithIllegalParameters() {
        assertThatThrownBy(() -> new SubmissionManager(null, recordCache, nodeLocalProperties, speedometers))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SubmissionManager(platform, null, nodeLocalProperties, speedometers))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SubmissionManager(platform, recordCache, null, speedometers))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SubmissionManager(platform, recordCache, nodeLocalProperties, null))
                .isInstanceOf(NullPointerException.class);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    void testSubmitWithIllegalParameters() {
        // given
        final var txBody = TransactionBody.newBuilder().build();

        // then
        assertThatThrownBy(() -> submissionManager.submit(null, bytes, parser))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> submissionManager.submit(txBody, null, parser))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> submissionManager.submit(txBody, bytes, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void testSuccess() throws PreCheckException {
        // given
        final TransactionID transactionID = TransactionID.newBuilder().build();
        final TransactionBody txBody =
                TransactionBody.newBuilder().setTransactionID(transactionID).build();
        when(platform.createTransaction(any())).thenReturn(true);

        // when
        submissionManager.submit(txBody, bytes, parser);

        // then
        verify(recordCache).addPreConsensus(transactionID);
        verify(speedometers, never()).cyclePlatformTxnRejections();
    }

    @Test
    void testSubmittingToPlatformFails() {
        // given
        final TransactionID transactionID = TransactionID.newBuilder().build();
        final TransactionBody txBody =
                TransactionBody.newBuilder().setTransactionID(transactionID).build();

        // when
        assertThatThrownBy(() -> submissionManager.submit(txBody, bytes, parser))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", PLATFORM_TRANSACTION_NOT_CREATED);

        // then
        verify(recordCache, never()).addPreConsensus(any());
        verify(speedometers).cyclePlatformTxnRejections();
    }

    @Test
    void testSuccessWithUncheckedSubmit() throws PreCheckException, InvalidProtocolBufferException {
        // given
        final ByteString payload = ByteString.copyFrom(new byte[] {0, 1, 2, 3});
        final UncheckedSubmitBody uncheckedSubmit =
                UncheckedSubmitBody.newBuilder().setTransactionBytes(payload).build();
        final TransactionBody uncheckedSubmitParsed =
                TransactionBody.newBuilder().build();
        final TransactionID transactionID = TransactionID.newBuilder().build();
        final TransactionBody txBody = TransactionBody.newBuilder()
                .setTransactionID(transactionID)
                .setUncheckedSubmit(uncheckedSubmit)
                .build();
        when(nodeLocalProperties.activeProfile()).thenReturn(Profile.TEST);
        when(parser.parseFrom(payload)).thenReturn(uncheckedSubmitParsed);
        when(platform.createTransaction(uncheckedSubmitParsed.toByteArray())).thenReturn(true);

        // when
        submissionManager.submit(txBody, bytes, parser);

        // then
        verify(recordCache).addPreConsensus(transactionID);
        verify(speedometers, never()).cyclePlatformTxnRejections();
    }

    @Test
    void testUncheckedSubmitInProdFails() {
        // given
        final ByteString payload = ByteString.copyFrom(new byte[] {0, 1, 2, 3});
        final UncheckedSubmitBody uncheckedSubmit =
                UncheckedSubmitBody.newBuilder().setTransactionBytes(payload).build();
        final TransactionID transactionID = TransactionID.newBuilder().build();
        final TransactionBody txBody = TransactionBody.newBuilder()
                .setTransactionID(transactionID)
                .setUncheckedSubmit(uncheckedSubmit)
                .build();
        when(nodeLocalProperties.activeProfile()).thenReturn(Profile.PROD);

        // when
        assertThatThrownBy(() -> submissionManager.submit(txBody, bytes, parser))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", PLATFORM_TRANSACTION_NOT_CREATED);

        // then
        verify(recordCache, never()).addPreConsensus(transactionID);
        verify(speedometers, never()).cyclePlatformTxnRejections();
    }

    @Test
    void testParsingUncheckedSubmitFails() throws InvalidProtocolBufferException {
        // given
        final ByteString payload = ByteString.copyFrom(new byte[] {0, 1, 2, 3});
        final UncheckedSubmitBody uncheckedSubmit =
                UncheckedSubmitBody.newBuilder().setTransactionBytes(payload).build();
        final TransactionID transactionID = TransactionID.newBuilder().build();
        final TransactionBody txBody = TransactionBody.newBuilder()
                .setTransactionID(transactionID)
                .setUncheckedSubmit(uncheckedSubmit)
                .build();
        when(nodeLocalProperties.activeProfile()).thenReturn(Profile.TEST);
        when(parser.parseFrom(payload)).thenThrow(new InvalidProtocolBufferException("Expected exception"));

        // when
        assertThatThrownBy(() -> submissionManager.submit(txBody, bytes, parser))
                .isInstanceOf(PreCheckException.class)
                .hasFieldOrPropertyWithValue("responseCode", PLATFORM_TRANSACTION_NOT_CREATED);

        // then
        verify(recordCache, never()).addPreConsensus(transactionID);
        verify(speedometers, never()).cyclePlatformTxnRejections();
    }
}
