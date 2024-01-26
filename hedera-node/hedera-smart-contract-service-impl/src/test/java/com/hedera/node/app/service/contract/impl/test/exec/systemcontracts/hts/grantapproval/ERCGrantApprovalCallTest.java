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

package com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.grantapproval;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.FUNGIBLE_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_FUNGIBLE_TOKEN_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.OWNER_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.REVOKE_APPROVAL_SPENDER_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.UNAUTHORIZED_SPENDER_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.asBytesResult;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Nft;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.scope.VerificationStrategy;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval.ERCGrantApprovalCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.grantapproval.GrantApprovalTranslator;
import com.hedera.node.app.service.contract.impl.records.ContractCallRecordBuilder;
import com.hedera.node.app.service.contract.impl.test.exec.systemcontracts.hts.HtsCallTestBase;
import com.hedera.node.app.service.token.ReadableAccountStore;
import java.math.BigInteger;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.frame.MessageFrame.State;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class ERCGrantApprovalCallTest extends HtsCallTestBase {

    private ERCGrantApprovalCall subject;

    @Mock
    private VerificationStrategy verificationStrategy;

    @Mock
    private SystemContractGasCalculator systemContractGasCalculator;

    @Mock
    private ContractCallRecordBuilder recordBuilder;

    @Mock
    private Nft nft;

    @Mock
    private Token token;

    @Mock
    private Account account;

    @Mock
    private MessageFrame frame;

    @Mock
    private ReadableAccountStore accountStore;

    @Test
    void erc20approve() {
        subject = new ERCGrantApprovalCall(
                mockEnhancement(),
                systemContractGasCalculator,
                verificationStrategy,
                OWNER_ID,
                FUNGIBLE_TOKEN_ID,
                UNAUTHORIZED_SPENDER_ID,
                BigInteger.valueOf(100L),
                TokenType.FUNGIBLE_COMMON);
        given(systemContractOperations.dispatch(
                        any(TransactionBody.class),
                        eq(verificationStrategy),
                        eq(OWNER_ID),
                        eq(ContractCallRecordBuilder.class)))
                .willReturn(recordBuilder);
        given(recordBuilder.status()).willReturn(ResponseCodeEnum.SUCCESS);
        given(nativeOperations.getAccount(anyLong())).willReturn(account);
        given(nativeOperations.readableAccountStore()).willReturn(accountStore);
        given(accountStore.getAccountById(any(AccountID.class))).willReturn(account);
        given(account.accountIdOrThrow())
                .willReturn(AccountID.newBuilder().accountNum(1).build());
        given(account.alias()).willReturn(com.hedera.pbj.runtime.io.buffer.Bytes.wrap(new byte[] {1, 2, 3}));
        final var result = subject.execute(frame).fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                asBytesResult(
                        GrantApprovalTranslator.ERC_GRANT_APPROVAL.getOutputs().encodeElements(true)),
                result.getOutput());
    }

    @Test
    void erc721approve() {
        subject = new ERCGrantApprovalCall(
                mockEnhancement(),
                systemContractGasCalculator,
                verificationStrategy,
                OWNER_ID,
                NON_FUNGIBLE_TOKEN_ID,
                UNAUTHORIZED_SPENDER_ID,
                BigInteger.valueOf(100L),
                TokenType.NON_FUNGIBLE_UNIQUE);
        given(systemContractOperations.dispatch(
                        any(TransactionBody.class),
                        eq(verificationStrategy),
                        eq(OWNER_ID),
                        eq(ContractCallRecordBuilder.class)))
                .willReturn(recordBuilder);
        given(recordBuilder.status()).willReturn(ResponseCodeEnum.SUCCESS);
        given(nativeOperations.getNft(NON_FUNGIBLE_TOKEN_ID.tokenNum(), 100L)).willReturn(nft);
        given(nativeOperations.getToken(NON_FUNGIBLE_TOKEN_ID.tokenNum())).willReturn(token);
        given(nativeOperations.getAccount(anyLong())).willReturn(account);
        given(nft.ownerIdOrElse(any())).willReturn(OWNER_ID);
        given(nativeOperations.readableAccountStore()).willReturn(accountStore);
        given(accountStore.getAccountById(any(AccountID.class))).willReturn(account);
        given(account.accountIdOrThrow())
                .willReturn(AccountID.newBuilder().accountNum(1).build());
        given(account.alias()).willReturn(com.hedera.pbj.runtime.io.buffer.Bytes.wrap(new byte[] {1, 2, 3}));
        final var result = subject.execute(frame).fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                asBytesResult(
                        GrantApprovalTranslator.ERC_GRANT_APPROVAL.getOutputs().encodeElements(true)),
                result.getOutput());
    }

    @Test
    void erc721approveFailsWithInvalidSpenderAllowance() {
        subject = new ERCGrantApprovalCall(
                mockEnhancement(),
                systemContractGasCalculator,
                verificationStrategy,
                OWNER_ID,
                NON_FUNGIBLE_TOKEN_ID,
                UNAUTHORIZED_SPENDER_ID,
                BigInteger.valueOf(100L),
                TokenType.NON_FUNGIBLE_UNIQUE);
        given(nativeOperations.getNft(NON_FUNGIBLE_TOKEN_ID.tokenNum(), 100L)).willReturn(nft);
        given(nativeOperations.getToken(NON_FUNGIBLE_TOKEN_ID.tokenNum())).willReturn(token);
        given(nativeOperations.getAccount(anyLong())).willReturn(null).willReturn(account);
        final var result = subject.execute(frame).fullResult().result();

        assertEquals(State.REVERT, result.getState());
        assertEquals(
                Bytes.wrap(ResponseCodeEnum.INVALID_ALLOWANCE_SPENDER_ID
                        .protoName()
                        .getBytes()),
                result.getOutput());
    }

    @Test
    void erc721approveFailsWithSenderDoesNotOwnNFTSerialNumber() {
        subject = new ERCGrantApprovalCall(
                mockEnhancement(),
                systemContractGasCalculator,
                verificationStrategy,
                OWNER_ID,
                NON_FUNGIBLE_TOKEN_ID,
                UNAUTHORIZED_SPENDER_ID,
                BigInteger.valueOf(100L),
                TokenType.NON_FUNGIBLE_UNIQUE);
        // make sure nft is found
        given(nativeOperations.getNft(NON_FUNGIBLE_TOKEN_ID.tokenNum(), 100L)).willReturn(nft);
        given(nativeOperations.getToken(NON_FUNGIBLE_TOKEN_ID.tokenNum())).willReturn(token);
        // sender account is found, but it is not the owner account
        given(nativeOperations.getAccount(anyLong())).willReturn(account);
        given(nft.ownerIdOrElse(any())).willReturn(UNAUTHORIZED_SPENDER_ID);

        final var result = subject.execute(frame).fullResult().result();

        assertEquals(State.REVERT, result.getState());
        assertEquals(
                Bytes.wrap(ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO
                        .protoName()
                        .getBytes()),
                result.getOutput());
    }

    @Test
    void erc721approveFailsWithInvalidTokenNFTSerialNumber() {
        subject = new ERCGrantApprovalCall(
                mockEnhancement(),
                systemContractGasCalculator,
                verificationStrategy,
                OWNER_ID,
                NON_FUNGIBLE_TOKEN_ID,
                UNAUTHORIZED_SPENDER_ID,
                BigInteger.valueOf(100L),
                TokenType.NON_FUNGIBLE_UNIQUE);
        // make sure nft is found
        given(nativeOperations.getNft(NON_FUNGIBLE_TOKEN_ID.tokenNum(), 100L)).willReturn(null);
        given(nativeOperations.getToken(NON_FUNGIBLE_TOKEN_ID.tokenNum())).willReturn(token);
        // sender account is found, but it is not the owner account
        given(nativeOperations.getAccount(anyLong())).willReturn(account);

        final var result = subject.execute(frame).fullResult().result();

        assertEquals(State.REVERT, result.getState());
        assertEquals(
                Bytes.wrap(ResponseCodeEnum.INVALID_TOKEN_NFT_SERIAL_NUMBER
                        .protoName()
                        .getBytes()),
                result.getOutput());
    }

    @Test
    void erc721revoke() {
        subject = new ERCGrantApprovalCall(
                mockEnhancement(),
                systemContractGasCalculator,
                verificationStrategy,
                OWNER_ID,
                NON_FUNGIBLE_TOKEN_ID,
                REVOKE_APPROVAL_SPENDER_ID,
                BigInteger.valueOf(100L),
                TokenType.NON_FUNGIBLE_UNIQUE);
        given(systemContractOperations.dispatch(
                        any(TransactionBody.class),
                        eq(verificationStrategy),
                        eq(OWNER_ID),
                        eq(ContractCallRecordBuilder.class)))
                .willReturn(recordBuilder);
        given(recordBuilder.status()).willReturn(ResponseCodeEnum.SUCCESS);
        given(nativeOperations.getNft(NON_FUNGIBLE_TOKEN_ID.tokenNum(), 100L)).willReturn(nft);
        given(nativeOperations.getToken(NON_FUNGIBLE_TOKEN_ID.tokenNum())).willReturn(token);
        given(nativeOperations.getAccount(anyLong())).willReturn(account);
        given(nft.ownerIdOrElse(any())).willReturn(OWNER_ID);
        given(nativeOperations.readableAccountStore()).willReturn(accountStore);
        given(accountStore.getAccountById(any(AccountID.class))).willReturn(account);
        given(account.accountIdOrThrow())
                .willReturn(AccountID.newBuilder().accountNum(1).build());
        given(account.alias()).willReturn(com.hedera.pbj.runtime.io.buffer.Bytes.wrap(new byte[] {1, 2, 3}));
        final var result = subject.execute(frame).fullResult().result();

        assertEquals(MessageFrame.State.COMPLETED_SUCCESS, result.getState());
        assertEquals(
                asBytesResult(
                        GrantApprovalTranslator.ERC_GRANT_APPROVAL.getOutputs().encodeElements(true)),
                result.getOutput());
    }
}
