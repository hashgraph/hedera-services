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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.freeze;

import static com.hedera.hapi.node.base.ResponseCodeEnum.SUCCESS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.A_NEW_ACCOUNT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.B_NEW_ACCOUNT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.EIP_1014_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_HEADLONG_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.asBytesResult;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.asHeadlongAddress;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.TupleType;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AddressIdConverter;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.freeze.FreezeCall;
import com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.HtsCallTestBase;
import com.hedera.node.app.service.token.records.CryptoTransferRecordBuilder;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import org.hyperledger.besu.evm.frame.MessageFrame.State;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class FreezeCallTest extends HtsCallTestBase {

    private static final TupleType INT64_ENCODER = TupleType.parse(ReturnTypes.INT_64);

    @Mock
    private AddressIdConverter addressIdConverter;

    @Mock
    private CryptoTransferRecordBuilder recordBuilder;

    @Mock
    private VerificationStrategy verificationStrategy;

    private final Address OWNER = asHeadlongAddress(EIP_1014_ADDRESS);
    private FreezeCall subject;

    @Test
    void freezeHappyPath() {
        subject = new FreezeCall(
                mockEnhancement(),
                addressIdConverter,
                verificationStrategy,
                A_NEW_ACCOUNT_ID,
                FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                OWNER);

        given(systemContractOperations.dispatch(
                        any(TransactionBody.class),
                        eq(verificationStrategy),
                        eq(A_NEW_ACCOUNT_ID),
                        eq(SingleTransactionRecordBuilder.class)))
                .willReturn(recordBuilder);
        given(recordBuilder.status()).willReturn(SUCCESS);

        final var result = subject.execute().fullResult().result();
        assertEquals(State.COMPLETED_SUCCESS, result.getState());
        assertEquals(asBytesResult(INT64_ENCODER.encodeElements((long) SUCCESS.protoOrdinal())), result.getOutput());
    }

    @Test
    void failsWithTokenNotAssociated() {
        subject = new FreezeCall(
                mockEnhancement(),
                addressIdConverter,
                verificationStrategy,
                B_NEW_ACCOUNT_ID,
                FUNGIBLE_TOKEN_HEADLONG_ADDRESS,
                OWNER);

        given(systemContractOperations.dispatch(
                        any(TransactionBody.class),
                        eq(verificationStrategy),
                        eq(B_NEW_ACCOUNT_ID),
                        eq(SingleTransactionRecordBuilder.class)))
                .willReturn(recordBuilder);
        given(recordBuilder.status()).willReturn(TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);

        final var result = subject.execute().fullResult().result();
        assertEquals(State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                asBytesResult(INT64_ENCODER.encodeElements((long) TOKEN_NOT_ASSOCIATED_TO_ACCOUNT.protoOrdinal())),
                result.getOutput());
    }
}
