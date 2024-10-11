/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.create;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_TX_FEE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_HAS_NO_SUPPLY_KEY;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.ZERO_ADDRESS;
import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.CONFIG_CONTEXT_VARIABLE;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.ALIASED_SOMEBODY;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.A_NEW_ACCOUNT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.DEFAULT_CONFIG;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.EIP_1014_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SENDER_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.asHeadlongAddress;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.readableRevertReason;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.token.TokenCreateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.ClassicCreatesCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.create.CreateTranslator;
import com.hedera.node.app.service.contract.impl.records.ContractCallStreamBuilder;
import com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.common.CallTestBase;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Deque;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.frame.BlockValues;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class ClassicCreatesCallTest extends CallTestBase {
    private static final org.hyperledger.besu.datatypes.Address FRAME_SENDER_ADDRESS = EIP_1014_ADDRESS;

    @Mock
    private VerificationStrategy verificationStrategy;

    @Mock
    private AddressIdConverter addressIdConverter;

    @Mock
    private ContractCallStreamBuilder recordBuilder;

    @Mock
    private BlockValues blockValues;

    private static final TransactionBody PRETEND_CREATE_TOKEN = TransactionBody.newBuilder()
            .tokenCreation(TokenCreateTransactionBody.newBuilder()
                    .symbol("FT")
                    .treasury(A_NEW_ACCOUNT_ID)
                    .autoRenewAccount(SENDER_ID)
                    .build())
            .build();

    private ClassicCreatesCall subject;

    private final Deque<MessageFrame> stack = new ArrayDeque<>();

    private final Address tokenId =
            Address.wrap(Address.toChecksumAddress(BigInteger.valueOf(FUNGIBLE_TOKEN_ID.tokenNum())));

    @Test
    void createFungibleTokenHappyPathV1() {
        commonGivens();
        given(recordBuilder.status()).willReturn(SUCCESS);

        final var result = subject.execute(frame).fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                Bytes.wrap(CreateTranslator.CREATE_FUNGIBLE_TOKEN_V1
                        .getOutputs()
                        .encodeElements((long) SUCCESS.protoOrdinal(), tokenId)
                        .array()),
                result.getOutput());
    }

    @Test
    void createFungibleTokenHappyPathV2() {
        commonGivens();
        given(recordBuilder.status()).willReturn(SUCCESS);

        final var result = subject.execute(frame).fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                Bytes.wrap(CreateTranslator.CREATE_FUNGIBLE_TOKEN_V2
                        .getOutputs()
                        .encodeElements((long) SUCCESS.protoOrdinal(), tokenId)
                        .array()),
                result.getOutput());
    }

    @Test
    void createFungibleTokenHappyPathV3() {
        commonGivens();
        given(recordBuilder.status()).willReturn(SUCCESS);

        final var result = subject.execute(frame).fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                Bytes.wrap(CreateTranslator.CREATE_FUNGIBLE_TOKEN_V3
                        .getOutputs()
                        .encodeElements((long) SUCCESS.protoOrdinal(), tokenId)
                        .array()),
                result.getOutput());
    }

    @Test
    void createFungibleTokenWithCustomFeesHappyPathV1() {
        commonGivens();
        given(recordBuilder.status()).willReturn(SUCCESS);

        final var result = subject.execute(frame).fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                Bytes.wrap(CreateTranslator.CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V1
                        .getOutputs()
                        .encodeElements((long) SUCCESS.protoOrdinal(), tokenId)
                        .array()),
                result.getOutput());
    }

    @Test
    void createFungibleTokenWithCustomFeesHappyPathV2() {
        commonGivens();
        given(recordBuilder.status()).willReturn(SUCCESS);

        final var result = subject.execute(frame).fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                Bytes.wrap(CreateTranslator.CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V2
                        .getOutputs()
                        .encodeElements((long) SUCCESS.protoOrdinal(), tokenId)
                        .array()),
                result.getOutput());
    }

    @Test
    void createFungibleTokenWithCustomFeesHappyPathV3() {
        commonGivens();
        given(recordBuilder.status()).willReturn(SUCCESS);

        final var result = subject.execute(frame).fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                Bytes.wrap(CreateTranslator.CREATE_FUNGIBLE_WITH_CUSTOM_FEES_V3
                        .getOutputs()
                        .encodeElements((long) SUCCESS.protoOrdinal(), tokenId)
                        .array()),
                result.getOutput());
    }

    @Test
    void createNonFungibleTokenHappyPathV1() {
        commonGivens();
        given(recordBuilder.status()).willReturn(SUCCESS);

        final var result = subject.execute(frame).fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V1
                        .getOutputs()
                        .encodeElements((long) SUCCESS.protoOrdinal(), tokenId)
                        .array()),
                result.getOutput());
    }

    @Test
    void createNonFungibleTokenHappyPathV2() {
        commonGivens();
        given(recordBuilder.status()).willReturn(SUCCESS);

        final var result = subject.execute(frame).fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V2
                        .getOutputs()
                        .encodeElements((long) SUCCESS.protoOrdinal(), tokenId)
                        .array()),
                result.getOutput());
    }

    @Test
    void createNonFungibleTokenHappyPathV3() {
        commonGivens();
        given(recordBuilder.status()).willReturn(SUCCESS);

        final var result = subject.execute(frame).fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_V3
                        .getOutputs()
                        .encodeElements((long) SUCCESS.protoOrdinal(), tokenId)
                        .array()),
                result.getOutput());
    }

    @Test
    void createNonFungibleTokenWithCustomFeesHappyPathV1() {
        commonGivens();
        given(recordBuilder.status()).willReturn(SUCCESS);

        final var result = subject.execute(frame).fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V1
                        .getOutputs()
                        .encodeElements((long) SUCCESS.protoOrdinal(), tokenId)
                        .array()),
                result.getOutput());
    }

    @Test
    void createNonFungibleTokenWithCustomFeesHappyPathV2() {
        commonGivens();
        given(recordBuilder.status()).willReturn(SUCCESS);

        final var result = subject.execute(frame).fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V2
                        .getOutputs()
                        .encodeElements((long) SUCCESS.protoOrdinal(), tokenId)
                        .array()),
                result.getOutput());
    }

    @Test
    void createNonFungibleTokenWithCustomFeesHappyPathV3() {
        commonGivens();
        given(recordBuilder.status()).willReturn(SUCCESS);

        final var result = subject.execute(frame).fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V3
                        .getOutputs()
                        .encodeElements((long) SUCCESS.protoOrdinal(), tokenId)
                        .array()),
                result.getOutput());
    }

    @Test
    void requiresNonGasCostToBeProvidedAsValue() {
        commonGivens(200_000L, 99_999L, true);
        given(recordBuilder.status()).willReturn(SUCCESS);
        given(systemContractOperations.externalizePreemptedDispatch(any(), eq(INSUFFICIENT_TX_FEE)))
                .willReturn(recordBuilder);

        final var result = subject.execute(frame).fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                Bytes.wrap(CreateTranslator.CREATE_NON_FUNGIBLE_TOKEN_WITH_CUSTOM_FEES_V3
                        .getOutputs()
                        .encodeElements((long) INSUFFICIENT_TX_FEE.protoOrdinal(), ZERO_ADDRESS)
                        .array()),
                result.getOutput());
    }

    @Test
    void createFungibleTokenUnhappyPathRevertsWithReason() {
        commonGivens();
        given(recordBuilder.status()).willReturn(TOKEN_HAS_NO_SUPPLY_KEY);

        final var result = subject.execute(frame).fullResult().result();

        assertEquals(MessageFrame.State.REVERT, result.getState());
        assertEquals(readableRevertReason(recordBuilder.status()), result.getOutput());
    }

    private void commonGivens() {
        commonGivens(0L, 0L, false);
    }

    private void commonGivens(long baseCost, long value, boolean shouldBePreempted) {
        given(frame.getValue()).willReturn(Wei.of(value));
        given(gasCalculator.feeCalculatorPriceInTinyBars(any(), any())).willReturn(baseCost);
        stack.push(frame);
        given(addressIdConverter.convert(asHeadlongAddress(FRAME_SENDER_ADDRESS)))
                .willReturn(A_NEW_ACCOUNT_ID);

        if (!shouldBePreempted) {
            given(frame.getMessageFrameStack()).willReturn(stack);
            given(frame.getContextVariable(CONFIG_CONTEXT_VARIABLE)).willReturn(DEFAULT_CONFIG);
            given(nativeOperations.getAccount(A_NEW_ACCOUNT_ID)).willReturn(ALIASED_SOMEBODY);
            given(systemContractOperations.dispatch(
                            any(TransactionBody.class),
                            eq(verificationStrategy),
                            eq(A_NEW_ACCOUNT_ID),
                            eq(ContractCallStreamBuilder.class)))
                    .willReturn(recordBuilder);
        }
        given(frame.getBlockValues()).willReturn(blockValues);
        given(blockValues.getTimestamp()).willReturn(Timestamp.DEFAULT.seconds());
        subject = new ClassicCreatesCall(
                gasCalculator,
                mockEnhancement(),
                PRETEND_CREATE_TOKEN,
                verificationStrategy,
                FRAME_SENDER_ADDRESS,
                addressIdConverter);

        lenient().when(recordBuilder.tokenID()).thenReturn(FUNGIBLE_TOKEN_ID);
    }
}
