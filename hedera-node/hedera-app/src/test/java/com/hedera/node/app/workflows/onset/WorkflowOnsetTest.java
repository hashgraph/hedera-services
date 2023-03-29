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

package com.hedera.node.app.workflows.onset;

import static com.hedera.hapi.node.base.HederaFunctionality.CONSENSUS_CREATE_TOPIC;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TRANSACTION_HAS_UNKNOWN_FIELDS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TRANSACTION_OVERSIZE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.SignaturePair;
import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.consensus.ConsensusCreateTopicTransactionBody;
import com.hedera.hapi.node.transaction.SignedTransaction;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.AppTestBase;
import com.hedera.node.app.spi.HapiUtils;
import com.hedera.node.app.spi.UnknownHederaFunctionality;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.pbj.runtime.Codec;
import com.hedera.pbj.runtime.io.buffer.BufferedData;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowOnsetTest extends AppTestBase {
    private static final int MAX_TX_SIZE = 1024 * 6;

    @Mock(strictness = LENIENT)
    private OnsetChecker checker;

    private Transaction tx;
    private SignatureMap signatureMap;
    private SignedTransaction signedTx;
    private TransactionBody txBody;
    private Bytes inputBuffer;
    private WorkflowOnset onset;

    /**
     * For these test, we will create an actual transaction and properly convert it to serialized
     * protobuf bytes. The {@link WorkflowOnset} will deserialize the bytes, and we need to make
     * sure it does so correctly!
     *
     * @throws PreCheckException should never be thrown
     */
    @BeforeEach
    void setup() throws PreCheckException {
        // An empty transaction body data. It isn't actually valid (i.e. the service implementation
        // would fail this empty transaction), but it is enough to test the WorkflowOnset.
        final var content = ConsensusCreateTopicTransactionBody.newBuilder().build();
        txBody = TransactionBody.newBuilder().consensusCreateTopic(content).build();

        // Create the signed transaction object. We hold a reference to it to make sure after
        // we parse the object we get the same thing back.
        final var bodyBytes = asBytes(TransactionBody.PROTOBUF, txBody);
        final var sigPair =
                SignaturePair.newBuilder().ed25519(Bytes.wrap(randomBytes(64))).build();
        signatureMap = SignatureMap.newBuilder().sigPair(sigPair).build();
        signedTx = SignedTransaction.newBuilder()
                .bodyBytes(bodyBytes)
                .sigMap(signatureMap)
                .build();

        // Create the transaction object
        final var signedTransactionBytes = asBytes(SignedTransaction.PROTOBUF, signedTx);
        tx = Transaction.newBuilder()
                .signedTransactionBytes(signedTransactionBytes)
                .build();
        inputBuffer = Bytes.wrap(asByteArray(tx));

        // Create the onset object
        onset = new WorkflowOnset(MAX_TX_SIZE, checker);

        // We mocked out the checker so it always succeeds
        when(checker.checkTransactionBody(any())).thenReturn(OK);
    }

    /**
     * It is not necessary to test all the possible failure reasons, just a few to make sure that
     * the workflow is passing the failure reason to the response.
     * @return a stream of arguments with the failure reason
     */
    public static Stream<Arguments> failureReasons() {
        return Stream.of(
                Arguments.of(INVALID_TRANSACTION),
                Arguments.of(INVALID_TRANSACTION_BODY),
                Arguments.of(TRANSACTION_HAS_UNKNOWN_FIELDS),
                Arguments.of(TRANSACTION_OVERSIZE));
    }

    @Nested
    @DisplayName("Constructor Tests")
    class ConstructorTest {
        @Test
        @SuppressWarnings("ConstantConditions")
        @DisplayName("Constructor throws on illegal arguments")
        void testConstructorWithIllegalArguments() {
            assertThatThrownBy(() -> new WorkflowOnset(-1, checker)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new WorkflowOnset(0, checker)).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new WorkflowOnset(MAX_TX_SIZE, null)).isInstanceOf(NullPointerException.class);
        }
    }

    /**
     * These tests cover the different happy paths for parsing and checking transactions.
     * The {@link OnsetChecker} performs many of the checks, and these are mocked out to
     * succeed in these tests, even if the actual serialized transaction might not have
     * been entirely valid (see {@link #setup()}).
     */
    @Nested
    @DisplayName("Happy Paths")
    class HappyTest {
        /**
         * This test verifies that, given a valid transaction encoded as bytes in a
         * {@link BufferedData}, the {@link WorkflowOnset} will parse it correctly. The
         * transaction in this case is using the "signed transaction bytes" fields.
         *
         * @throws PreCheckException Not throw by this test if all goes well
         */
        @Test
        @DisplayName("A valid transaction passes parseAndCheck with a BufferedData")
        void testParseAndCheckSucceeds() throws PreCheckException {
            // Given a valid serialized transaction, when we parse and check
            final var result = onset.parseAndCheck(inputBuffer);

            // Then the parsed data is as we expected
            assertThat(result.errorCode()).isEqualTo(OK);
            assertThat(result.txBody()).isEqualTo(txBody);
            assertThat(result.signatureMap()).isEqualTo(signatureMap);
            assertThat(result.functionality()).isEqualTo(CONSENSUS_CREATE_TOPIC);
        }

        /**
         * This test is the same as {@link #testParseAndCheckSucceeds()} except that it uses a
         * bte array instead of a {@link BufferedData}.
         *
         * @throws PreCheckException Not throw by this test if all goes well
         */
        @Test
        @DisplayName("A valid transaction passes parseAndCheck with a byte[]")
        void testParseAndCheckWithByteArraySucceeds() throws PreCheckException {
            // Given the transaction encoded in a byte array
            final var byteArray = asByteArray(tx);

            // When we parse and check
            final var result = onset.parseAndCheck(Bytes.wrap(byteArray));

            // Then the parsed data is as we expected
            assertThat(result.txBody()).isEqualTo(txBody);
            assertThat(result.errorCode()).isEqualTo(OK);
            assertThat(result.signatureMap()).isEqualTo(signatureMap);
            assertThat(result.functionality()).isEqualTo(CONSENSUS_CREATE_TOPIC);
        }

        /**
         * This test is the same as {@link #testParseAndCheckSucceeds()} except that instead of
         * using "signed transaction bytes" in the transaction, it uses the deprecated fields.
         *
         * @throws PreCheckException Not throw by this test if all goes well
         */
        @Test
        @DisplayName("A transaction with deprecated fields passes parseAndCheck")
        void testParseAndCheckWithDeprecatedFieldsSucceeds() throws PreCheckException {
            // Given a transaction using the deprecated fields
            final var localTx = Transaction.newBuilder()
                    .bodyBytes(signedTx.bodyBytes())
                    .sigMap(signedTx.sigMap())
                    .build();
            inputBuffer = Bytes.wrap(asByteArray(localTx));

            // When we parse and check
            final var result = onset.parseAndCheck(inputBuffer);

            // Then everything works because the deprecated fields are supported
            assertThat(result.txBody()).isEqualTo(txBody);
            assertThat(result.errorCode()).isEqualTo(OK);
            assertThat(result.signatureMap()).isEqualTo(signatureMap);
            assertThat(result.functionality()).isEqualTo(CONSENSUS_CREATE_TOPIC);
        }
    }

    /**
     * These tests verify that illegal arguments to the `check` and `parseAndCheck` methods
     * are correctly rejected.
     */
    @Nested
    @DisplayName("`parseAndCheck` and `check` Argument Tests")
    class ParseAndCheckArgTest {
        @Test
        @SuppressWarnings("ConstantConditions")
        @DisplayName("`parseAndCheck` with a BufferedData rejects null args")
        void parseAndCheckIllegalArgs() {
            assertThatThrownBy(() -> onset.parseAndCheck(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @SuppressWarnings("ConstantConditions")
        @DisplayName("`check` rejects null args")
        void checkObjectIllegalArgs() {
            assertThatThrownBy(() -> onset.check(null)).isInstanceOf(NullPointerException.class);
        }
    }

    /**
     * These tests cover the different failure paths for parsing and checking transactions.
     */
    @Nested
    @DisplayName("`doParseAndCheck` Tests")
    class ParseAndCheckTest {
        @Test
        @DisplayName("0. Fail fast if there are too many transaction bytes")
        void tooManyBytes() {
            // Given an empty set of bytes
            inputBuffer = Bytes.wrap(randomBytes(MAX_TX_SIZE + 1));

            // When we parse and check, we find that the buffer has too many bytes
            assertThatThrownBy(() -> onset.parseAndCheck(inputBuffer))
                    .isInstanceOf(PreCheckException.class)
                    .hasFieldOrPropertyWithValue("responseCode", TRANSACTION_OVERSIZE);
        }

        @Nested
        @DisplayName("1. Parse and validate transaction object")
        class ParseAndValidateTransactionTest {
            @Test
            @DisplayName("If the transaction bytes are not valid protobuf, it will fail")
            void badTransactionProtobuf() {
                // Given an invalid protobuf message
                inputBuffer = Bytes.wrap(invalidProtobuf());

                // When we parse and check, then the parsing fails because this is an INVALID_TRANSACTION
                assertThatThrownBy(() -> onset.parseAndCheck(inputBuffer))
                        .isInstanceOf(PreCheckException.class)
                        .hasFieldOrPropertyWithValue("responseCode", INVALID_TRANSACTION);
            }

            @Test
            @DisplayName("If the transaction protobuf has unknown fields, then fail")
            void unknownFieldInTransaction() {
                // Given a valid protobuf but with an unknown field
                inputBuffer = Bytes.wrap(appendUnknownField(asByteArray(tx)));

                // When we parse and check, then the parsing fails because has unknown fields
                assertThatThrownBy(() -> onset.parseAndCheck(inputBuffer))
                        .isInstanceOf(PreCheckException.class)
                        .hasFieldOrPropertyWithValue("responseCode", TRANSACTION_HAS_UNKNOWN_FIELDS);
            }

            @ParameterizedTest(name = "Transaction checking fails with error code {0}")
            @MethodSource("com.hedera.node.app.workflows.onset.WorkflowOnsetTest#failureReasons")
            @DisplayName("Verify that various PreCheckExceptions that fail while checking transactions")
            void onsetFailsWithPreCheckException(ResponseCodeEnum failureReason) throws PreCheckException {
                // Given a checker that will throw a PreCheckException with the given failure reason
                doThrow(new PreCheckException(failureReason)).when(checker).checkTransaction(any());

                // When we parse and check, then the parsing fails in checkTransactionBody
                assertThatThrownBy(() -> onset.parseAndCheck(inputBuffer))
                        .isInstanceOf(PreCheckException.class)
                        .hasFieldOrPropertyWithValue("responseCode", failureReason);
            }
        }

        @Nested
        @DisplayName("2. Parse and validate the signed transaction (if available)")
        class ParseAndValidateSignedTransactionTest {
            @Test
            @DisplayName("If the signed transaction bytes are not valid protobuf, it will fail")
            void badSignedTransactionProtobuf() {
                // Given an invalid protobuf message
                final var localTx = Transaction.newBuilder()
                        .signedTransactionBytes(Bytes.wrap(invalidProtobuf()))
                        .build();
                inputBuffer = Bytes.wrap(asByteArray(localTx));

                // When we parse and check, then the parsing fails because this is an INVALID_TRANSACTION
                assertThatThrownBy(() -> onset.parseAndCheck(inputBuffer))
                        .isInstanceOf(PreCheckException.class)
                        .hasFieldOrPropertyWithValue("responseCode", INVALID_TRANSACTION);
            }

            @Test
            @DisplayName("If the signed transaction protobuf has unknown fields, then fail")
            void unknownFieldInSignedTransaction() {
                // Given a valid protobuf but with an unknown field
                final var badSignedTxBytes = appendUnknownField(SignedTransaction.PROTOBUF, signedTx);
                tx = Transaction.newBuilder()
                        .signedTransactionBytes(badSignedTxBytes)
                        .build();
                inputBuffer = Bytes.wrap(asByteArray(tx));

                // When we parse and check, then the parsing fails because has unknown fields
                assertThatThrownBy(() -> onset.parseAndCheck(inputBuffer))
                        .isInstanceOf(PreCheckException.class)
                        .hasFieldOrPropertyWithValue("responseCode", TRANSACTION_HAS_UNKNOWN_FIELDS);
            }
        }

        @Nested
        @DisplayName("3. Parse and validate TransactionBody")
        class ParseAndValidateTransactionBodyTest {
            @Test
            @DisplayName("If the transaction body bytes are not valid protobuf, it will fail")
            void badTransactionBodyProtobuf() {
                // Given an invalid protobuf message
                signedTx = SignedTransaction.newBuilder()
                        .bodyBytes(Bytes.wrap(invalidProtobuf()))
                        .sigMap(signatureMap)
                        .build();

                final var signedTransactionBytes = asBytes(SignedTransaction.PROTOBUF, signedTx);
                tx = Transaction.newBuilder()
                        .signedTransactionBytes(signedTransactionBytes)
                        .build();
                inputBuffer = Bytes.wrap(asByteArray(tx));

                // When we parse and check, then the parsing fails because has unknown fields
                assertThatThrownBy(() -> onset.parseAndCheck(inputBuffer))
                        .isInstanceOf(PreCheckException.class)
                        .hasFieldOrPropertyWithValue("responseCode", INVALID_TRANSACTION_BODY);
            }

            @Test
            @DisplayName("If the transaction body protobuf has unknown fields, then fail")
            void unknownFieldInTransactionBody() {
                // Given a valid protobuf but with an unknown field
                final var badBodyBytes = appendUnknownField(TransactionBody.PROTOBUF, txBody);
                signedTx = SignedTransaction.newBuilder()
                        .bodyBytes(badBodyBytes)
                        .sigMap(signatureMap)
                        .build();

                final var signedTransactionBytes = asBytes(SignedTransaction.PROTOBUF, signedTx);
                tx = Transaction.newBuilder()
                        .signedTransactionBytes(signedTransactionBytes)
                        .build();
                inputBuffer = Bytes.wrap(asByteArray(tx));

                // When we parse and check, then the parsing fails because this is an TRANSACTION_HAS_UNKNOWN_FIELDS
                assertThatThrownBy(() -> onset.parseAndCheck(inputBuffer))
                        .isInstanceOf(PreCheckException.class)
                        .hasFieldOrPropertyWithValue("responseCode", TRANSACTION_HAS_UNKNOWN_FIELDS);
            }

            @ParameterizedTest(name = "Transaction Body checking fails with error code {0}")
            @MethodSource("com.hedera.node.app.workflows.onset.WorkflowOnsetTest#failureReasons")
            @DisplayName("Verify that various PreCheckExceptions that fail while checking transaction bodies")
            void onsetFailsWithPreCheckException(ResponseCodeEnum failureReason) throws PreCheckException {
                // Given a checker that will throw a PreCheckException with the given failure reason
                when(checker.checkTransactionBody(any())).thenThrow(new PreCheckException(failureReason));

                // When we parse and check, then the parsing fails in checkTransactionBody
                assertThatThrownBy(() -> onset.parseAndCheck(inputBuffer))
                        .isInstanceOf(PreCheckException.class)
                        .hasFieldOrPropertyWithValue("responseCode", failureReason);
            }
        }

        @Nested
        @DisplayName("4. return TransactionBody")
        class CreateTransactionBodyTest {
            @Test
            @DisplayName("If we ever cannot determine the HederaFunctionality, then we throw an exception")
            void unknownFunctionality() {
                try (MockedStatic<HapiUtils> hapiUtils = mockStatic(HapiUtils.class)) {
                    // Given a HederaFunctionality that is unknown
                    hapiUtils.when(() -> HapiUtils.functionOf(eq(txBody))).thenThrow(new UnknownHederaFunctionality());

                    // When we parse and check, then the parsing fails due to the exception
                    assertThatThrownBy(() -> onset.parseAndCheck(inputBuffer))
                            .isInstanceOf(PreCheckException.class)
                            .hasFieldOrPropertyWithValue("responseCode", INVALID_TRANSACTION_BODY);
                }
            }
        }

        @Test
        @DisplayName("If some random exception is thrown from OnsetChecker, the exception is bubbled up")
        void randomException() throws PreCheckException {
            // Given a OnsetChecker that will throw a RuntimeException
            when(checker.checkTransactionBody(any())).thenThrow(new RuntimeException("checkTransactionBody exception"));

            // When we parse and check, then the check fails with the runtime exception
            assertThatThrownBy(() -> onset.parseAndCheck(inputBuffer))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("checkTransactionBody exception");
        }
    }

    private byte[] invalidProtobuf() {
        // The first byte is the "tag" and the high order 5 bits are the field number. The low order 3 bits
        // are the wire type. But the field number is 0, which is not valid. And the wire
        // type is 7 which is not a valid wire type. So this is doubly wrong.
        return new byte[] {0b00000111};
    }

    private <T extends Record> Bytes appendUnknownField(@NonNull final Codec<T> codec, T tx) {
        final var bytes = asByteArray(codec, tx);
        return Bytes.wrap(appendUnknownField(bytes));
    }

    private byte[] appendUnknownField(@NonNull final byte[] bytes) {
        // We'll take the bytes and append a field number of 255 (which none of our protobuf objects use),
        // which is guaranteed to be a field that we don't know about. When we parse the protobuf,
        // we will encounter an unknown field, and this will allow us to verify that we fail when we
        // parse unknown fields. Since the 255 is varInt encoded, it will take 2 bytes and looks a
        // little wonky.
        final var arr = new byte[bytes.length + 2];
        System.arraycopy(bytes, 0, arr, 0, bytes.length);
        arr[arr.length - 2] = (byte) 0b11111000;
        arr[arr.length - 1] = (byte) 0b00001111;
        return arr;
    }
}
