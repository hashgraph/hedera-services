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
package com.hedera.services.contracts.execution;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseType.ANSWER_ONLY;

import com.google.protobuf.ByteString;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.contracts.EntityAccess;
import com.hedera.services.store.models.Account;
import com.hedera.services.utils.EntityIdUtils;
import com.hedera.services.utils.ResponseCodeUtil;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.ContractCallLocalQuery;
import com.hederahashgraph.api.proto.java.ContractCallLocalResponse;
import com.hederahashgraph.builder.RequestBuilder;
import com.swirlds.common.utility.CommonUtils;
import javax.inject.Singleton;
import org.apache.tuweni.bytes.Bytes;

/**
 * Utility class for executing static EVM calls for {@link
 * com.hedera.services.queries.contract.ContractCallLocalAnswer} and {@link
 * com.hedera.services.fees.calculation.contract.queries.ContractCallLocalResourceUsage}
 */
@Singleton
public class CallLocalExecutor {
    private CallLocalExecutor() {
        throw new UnsupportedOperationException("Utility Class");
    }

    /**
     * Executes the specified {@link ContractCallLocalQuery} through a static call. Parses the
     * result from the {@link CallLocalEvmTxProcessor} and sets the appropriate {@link
     * com.hederahashgraph.api.proto.java.ResponseCode}
     *
     * @param accountStore the account store
     * @param evmTxProcessor the {@link CallLocalEvmTxProcessor} processor
     * @param op the query to answer
     * @return {@link ContractCallLocalResponse} result of the execution
     */
    public static ContractCallLocalResponse execute(
            final AccountStore accountStore,
            final CallLocalEvmTxProcessor evmTxProcessor,
            final ContractCallLocalQuery op,
            final AliasManager aliasManager,
            final EntityAccess entityAccess) {
        try {
            final var paymentTxn =
                    SignedTxnAccessor.uncheckedFrom(op.getHeader().getPayment()).getTxn();
            final var senderId =
                    EntityIdUtils.unaliased(
                                    op.hasSenderId()
                                            ? op.getSenderId()
                                            : paymentTxn.getTransactionID().getAccountID(),
                                    aliasManager)
                            .toId();
            final var idOrAlias = op.getContractID();
            final var contractId = EntityIdUtils.unaliased(idOrAlias, aliasManager).toId();

            /* --- Load the model objects --- */
            final var sender = accountStore.loadAccount(senderId);
            var receiver =
                    entityAccess.isTokenAccount(contractId.asEvmAddress())
                            ? new Account(contractId)
                            : accountStore.loadContract(contractId);
            final var callData =
                    !op.getFunctionParameters().isEmpty()
                            ? Bytes.fromHexString(
                                    CommonUtils.hex(op.getFunctionParameters().toByteArray()))
                            : Bytes.EMPTY;

            /* --- Do the business logic --- */
            final var result =
                    evmTxProcessor.execute(
                            sender, receiver.canonicalAddress(), op.getGas(), 0, callData);

            var status = ResponseCodeUtil.getStatusOrDefault(result, OK);

            final var responseHeader =
                    RequestBuilder.getResponseHeader(status, 0L, ANSWER_ONLY, ByteString.EMPTY);

            return ContractCallLocalResponse.newBuilder()
                    .setHeader(responseHeader)
                    .setFunctionResult(result.toGrpc())
                    .build();
        } catch (InvalidTransactionException ite) {
            final var responseHeader =
                    RequestBuilder.getResponseHeader(
                            ite.getResponseCode(), 0L, ANSWER_ONLY, ByteString.EMPTY);

            return ContractCallLocalResponse.newBuilder().setHeader(responseHeader).build();
        }
    }
}
