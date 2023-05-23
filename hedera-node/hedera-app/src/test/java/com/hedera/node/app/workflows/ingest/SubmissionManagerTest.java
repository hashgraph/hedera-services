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

package com.hedera.node.app.workflows.ingest;

import static com.hedera.hapi.node.base.ResponseCodeEnum.DUPLICATE_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PLATFORM_TRANSACTION_NOT_CREATED;
import static java.lang.Thread.sleep;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.hapi.node.transaction.UncheckedSubmitBody;
import com.hedera.node.app.AppTestBase;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.context.properties.NodeLocalProperties;
import com.hedera.node.app.service.mono.context.properties.Profile;
import com.hedera.node.app.service.mono.pbj.PbjConverter;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.system.Platform;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class SubmissionManagerTest extends AppTestBase {
    /** A mocked {@link Platform} for accepting or rejecting submission of transaction bytes */
    @Mock
    private Platform platform;
    /** Mocked global properties to verify default transaction duration */
    @Mock
    private GlobalDynamicProperties globalProps;
    /** Mocked local properties to verify that we ONLY support Unchecked Submit when in PROD mode */
    @Mock
    private NodeLocalProperties nodeProps;

    @Test
    @DisplayName("Null cannot be provided as any of the constructor args")
    @SuppressWarnings("ConstantConditions")
    void testConstructorWithIllegalParameters() {
        assertThatThrownBy(() -> new SubmissionManager(null, globalProps, nodeProps, metrics))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SubmissionManager(platform, null, nodeProps, metrics))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SubmissionManager(platform, globalProps, null, metrics))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new SubmissionManager(platform, globalProps, nodeProps, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Nested
    @DisplayName("Tests for normal transaction submission")
    class SubmitTest extends AppTestBase {
        /** Mocked Metrics allowing us to see if the speedometer has been modified */
        @Mock
        private Metrics mockedMetrics;
        /** The speedometer metric used by the submission manager */
        @Mock
        private SpeedometerMetric platformTxnRejections;
        /** The submission manager instance */
        private SubmissionManager submissionManager;
        /** Representative of the raw transaction bytes */
        private Bytes bytes;
        /** The TransactionBody of the transaction we are submitting */
        private TransactionBody txBody;

        @BeforeEach
        void setup() {
            bytes = randomBytes(25);
            when(mockedMetrics.getOrCreate(any())).thenReturn(platformTxnRejections);
            submissionManager = new SubmissionManager(platform, globalProps, nodeProps, mockedMetrics);
            txBody = TransactionBody.newBuilder()
                    .transactionID(TransactionID.newBuilder()
                            .transactionValidStart(asTimestamp(Instant.now()))
                            .build())
                    .build();
        }

        @Test
        @DisplayName("Null cannot be provided as any of the 'submit' args")
        @SuppressWarnings("ConstantConditions")
        void testSubmitWithIllegalParameters() {
            assertThatThrownBy(() -> submissionManager.submit(null, bytes)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> submissionManager.submit(txBody, null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("Submission of the transaction to the platform is a success")
        void submittingToPlatformSucceeds() throws PreCheckException {
            // Given a platform that will succeed in taking bytes
            when(platform.createTransaction(any())).thenReturn(true);
            when(globalProps.maxTxnDuration()).thenReturn(60 * 3L);

            // When we submit bytes
            submissionManager.submit(txBody, bytes);

            // Then the platform actually receives the bytes
            verify(platform).createTransaction(PbjConverter.asBytes(bytes));
            // And the metrics keeping track of errors submitting are NOT touched
            verify(platformTxnRejections, never()).cycle();
        }

        @Test
        @DisplayName("If the platform fails to onConsensusRound the bytes, a PreCheckException is thrown")
        void testSubmittingToPlatformFails() {
            // Given a platform that will **fail** in taking bytes
            when(platform.createTransaction(any())).thenReturn(false);
            when(globalProps.maxTxnDuration()).thenReturn(60 * 3L);

            // When we submit bytes, then we fail by exception
            assertThatThrownBy(() -> submissionManager.submit(txBody, bytes))
                    .isInstanceOf(PreCheckException.class)
                    .extracting(t -> ((PreCheckException) t).responseCode())
                    .isEqualTo(PLATFORM_TRANSACTION_NOT_CREATED);
            // And the error metrics HAVE been updated
            verify(platformTxnRejections).cycle();
        }

        @Test
        @DisplayName("Submitting the same transaction twice in close succession rejects the duplicate")
        void testSubmittingDuplicateTransactionsCloseTogether() throws PreCheckException {
            // Given a platform that will succeed in taking bytes
            when(platform.createTransaction(any())).thenReturn(true);
            when(globalProps.maxTxnDuration()).thenReturn(60 * 3L);

            // When we submit a duplicate transaction twice in close succession, then the second one fails
            // with a DUPLICATE_TRANSACTION error
            submissionManager.submit(txBody, bytes);
            assertThatThrownBy(() -> submissionManager.submit(txBody, bytes))
                    .isInstanceOf(PreCheckException.class)
                    .extracting(t -> ((PreCheckException) t).responseCode())
                    .isEqualTo(DUPLICATE_TRANSACTION);
        }

        @Test
        @DisplayName("Transactions are removed from the cache as they expire")
        void testExpiration() throws PreCheckException, InterruptedException {
            // This test is a little weird in that it's testing a scenario that should NEVER be possible in the real
            // world, because earlier code in the workflow would not allow a transaction to get to the submission
            // manager with a valid start time that is in the past. However, we can exploit this loophole in this test
            // to verify that the expiration logic works as expected without having to make the expiration cache
            // directly testable. An alternative would be to use reflection to get access to the private field, or
            // make it package private to give the test direct access.
            when(platform.createTransaction(any())).thenReturn(true);
            when(globalProps.maxTxnDuration()).thenReturn(1L); // 1 second max duration

            // When I submit the first transaction, then wait the max duration, then submit the second transaction,
            // then we find the second one succeeds. This could not happen if the first transaction was still in the
            // expiration cache.
            submissionManager.submit(txBody, bytes);
            verify(platform).createTransaction(PbjConverter.asBytes(bytes));
            sleep(1001);
            submissionManager.submit(txBody, bytes);
            verify(platform, times(2)).createTransaction(PbjConverter.asBytes(bytes));
        }
    }

    @Nested
    @DisplayName("Tests for unchecked transaction submission")
    class UncheckedSubmitTest extends AppTestBase {
        /** Mocked Metrics allowing us to see if the speedometer has been modified */
        @Mock
        private Metrics mockedMetrics;
        /** The speedometer metric used by the submission manager */
        @Mock
        private SpeedometerMetric platformTxnRejections;
        /** The submission manager instance */
        private SubmissionManager submissionManager;
        /** Representative of the raw transaction bytes */
        private Bytes bytes;
        /** The TransactionBody of the transaction we are submitting */
        private TransactionBody txBody;
        /** Representative of the unchecked transaction bytes */
        private byte[] uncheckedBytes;

        @BeforeEach
        void setup() {
            when(nodeProps.activeProfile()).thenReturn(Profile.TEST);
            when(mockedMetrics.getOrCreate(any())).thenReturn(platformTxnRejections);
            submissionManager = new SubmissionManager(platform, globalProps, nodeProps, mockedMetrics);

            bytes = randomBytes(25);

            final var uncheckedTx = simpleCryptoTransfer();
            uncheckedBytes = asByteArray(uncheckedTx);
            txBody = TransactionBody.newBuilder()
                    .transactionID(TransactionID.newBuilder()
                            .transactionValidStart(asTimestamp(Instant.now()))
                            .build())
                    .uncheckedSubmit(UncheckedSubmitBody.newBuilder()
                            .transactionBytes(Bytes.wrap(uncheckedBytes))
                            .build())
                    .build();
        }

        @Test
        @DisplayName("An unchecked transaction not in PROD mode can be submitted")
        void testSuccessWithUncheckedSubmit() throws PreCheckException {
            // Given a platform that will succeed in taking the *unchecked* bytes
            when(platform.createTransaction(uncheckedBytes)).thenReturn(true);
            when(globalProps.maxTxnDuration()).thenReturn(60 * 3L);

            // When we submit an unchecked transaction, and separate bytes
            submissionManager.submit(txBody, bytes);

            // Then the platform actually sees the unchecked bytes
            verify(platform).createTransaction(uncheckedBytes);
            // And the metrics keeping track of errors submitting are NOT touched
            verify(platformTxnRejections, never()).cycle();
        }

        @Test
        @DisplayName("An unchecked transaction in PROD mode WILL FAIL")
        void testUncheckedSubmitInProdFails() {
            // Given we are in PROD mode
            when(nodeProps.activeProfile()).thenReturn(Profile.PROD);
            submissionManager = new SubmissionManager(platform, globalProps, nodeProps, mockedMetrics);

            // When we submit an unchecked transaction, and separate bytes, then the
            // submission FAILS because we are in PROD mode
            assertThatThrownBy(() -> submissionManager.submit(txBody, bytes))
                    .isInstanceOf(PreCheckException.class)
                    .hasFieldOrPropertyWithValue("responseCode", PLATFORM_TRANSACTION_NOT_CREATED);

            // Then the platform NEVER sees the unchecked bytes
            verify(platform, never()).createTransaction(uncheckedBytes);
            // We never attempted to submit this tx to the platform, so we don't increase the metric
            verify(platformTxnRejections, never()).cycle();
        }

        // TEST: If the unchecked submit is bogus bytes, or fails the onset check in some way, then
        // it must be rejected
        @Test
        @DisplayName("Send bogus bytes as an unchecked transaction and verify it fails with a PreCheckException")
        void testBogusBytes() {
            // Given we are in TEST mode and have a transaction with bogus bytes
            when(nodeProps.activeProfile()).thenReturn(Profile.TEST);
            submissionManager = new SubmissionManager(platform, globalProps, nodeProps, mockedMetrics);
            txBody = TransactionBody.newBuilder()
                    .transactionID(TransactionID.newBuilder()
                            .transactionValidStart(asTimestamp(Instant.now()))
                            .build())
                    .uncheckedSubmit(UncheckedSubmitBody.newBuilder()
                            .transactionBytes(randomBytes(25))
                            .build())
                    .build();

            // When we submit an unchecked transaction with bogus bytes, and separate bytes, then the
            // submission FAILS because of the bogus bytes
            assertThatThrownBy(() -> submissionManager.submit(txBody, bytes))
                    .isInstanceOf(PreCheckException.class)
                    .hasFieldOrPropertyWithValue("responseCode", PLATFORM_TRANSACTION_NOT_CREATED);

            // Then the platform NEVER sees the unchecked bytes
            verify(platform, never()).createTransaction(uncheckedBytes);
        }
    }
}
