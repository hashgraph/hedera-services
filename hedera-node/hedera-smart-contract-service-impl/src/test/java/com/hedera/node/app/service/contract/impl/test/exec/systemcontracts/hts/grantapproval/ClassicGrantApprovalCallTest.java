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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.grantapproval;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_FUNGIBLE_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.OWNER_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.UNAUTHORIZED_SPENDER_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.asBytesResult;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval.ClassicGrantApprovalCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval.GrantApprovalTranslator;
import com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.HtsCallTestBase;
import com.hedera.node.app.service.token.records.CryptoTransferRecordBuilder;
import java.math.BigInteger;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class ClassicGrantApprovalCallTest extends HtsCallTestBase {

    private ClassicGrantApprovalCall subject;

    @Mock
    private VerificationStrategy verificationStrategy;

    @Mock
    private CryptoTransferRecordBuilder recordBuilder;

    @Test
    void fungibleApprove() {
        subject = new ClassicGrantApprovalCall(
                mockEnhancement(),
                verificationStrategy,
                OWNER_ID,
                FUNGIBLE_TOKEN_ID,
                UNAUTHORIZED_SPENDER_ID,
                BigInteger.valueOf(100L),
                TokenType.FUNGIBLE_COMMON);
        given(systemContractOperations.dispatch(any(), any(), any(), any())).willReturn(recordBuilder);
        given(recordBuilder.status()).willReturn(ResponseCodeEnum.SUCCESS);
        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                asBytesResult(
                        GrantApprovalTranslator.GRANT_APPROVAL.getOutputs().encodeElements((long)
                                ResponseCodeEnum.SUCCESS.protoOrdinal())),
                result.getOutput());
    }

    @Test
    void nftApprove() {
        subject = new ClassicGrantApprovalCall(
                mockEnhancement(),
                verificationStrategy,
                OWNER_ID,
                NON_FUNGIBLE_TOKEN_ID,
                UNAUTHORIZED_SPENDER_ID,
                BigInteger.valueOf(100L),
                TokenType.NON_FUNGIBLE_UNIQUE);
        given(systemContractOperations.dispatch(any(), any(), any(), any())).willReturn(recordBuilder);
        given(recordBuilder.status()).willReturn(ResponseCodeEnum.SUCCESS);
        final var result = subject.execute().fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                asBytesResult(
                        GrantApprovalTranslator.GRANT_APPROVAL_NFT.getOutputs().encodeElements((long)
                                ResponseCodeEnum.SUCCESS.protoOrdinal())),
                result.getOutput());
    }
}
