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

package com.hedera.node.app.service.contract.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.OK;
import static com.hedera.hapi.node.base.ResponseType.ANSWER_ONLY;
import static com.hedera.node.app.service.token.api.AccountSummariesApi.hexedEvmAddressOf;
import static com.hedera.node.app.service.token.api.AccountSummariesApi.summarizeStakingInfo;
import static com.hedera.node.app.service.token.api.AccountSummariesApi.tokenRelationshipsOf;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateFalsePreCheck;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.QueryHeader;
import com.hedera.hapi.node.base.ResponseHeader;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.contract.ContractGetInfoResponse;
import com.hedera.hapi.node.contract.ContractInfo;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableNetworkStakingRewardsStore;
import com.hedera.node.app.service.token.ReadableStakingInfoStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import com.hedera.node.app.service.token.ReadableTokenStore;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.workflows.PaidQueryHandler;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.QueryContext;
import com.hedera.node.config.data.LedgerConfig;
import com.hedera.node.config.data.StakingConfig;
import com.hedera.node.config.data.TokensConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link HederaFunctionality#CONTRACT_GET_INFO}.
 */
@Singleton
public class ContractGetInfoHandler extends PaidQueryHandler {
    private static final long BYTES_PER_EVM_KEY_VALUE_PAIR = 64;

    @Inject
    public ContractGetInfoHandler() {
        // Dagger2
    }

    @Override
    public QueryHeader extractHeader(@NonNull final Query query) {
        requireNonNull(query);
        return query.contractGetInfoOrThrow().header();
    }

    @Override
    public Response createEmptyResponse(@NonNull final ResponseHeader header) {
        requireNonNull(header);
        final var response = ContractGetInfoResponse.newBuilder().header(header);
        return Response.newBuilder().contractGetInfo(response).build();
    }

    @Override
    public void validate(@NonNull final QueryContext context) throws PreCheckException {
        requireNonNull(context);
        validateFalsePreCheck(contractFrom(context) == null, INVALID_CONTRACT_ID);
    }

    @Override
    public Response findResponse(@NonNull final QueryContext context, @NonNull final ResponseHeader header) {
        requireNonNull(context);
        requireNonNull(header);
        final var contractGetInfo = ContractGetInfoResponse.newBuilder().header(header);
        if (header.nodeTransactionPrecheckCode() == OK && header.responseType() == ANSWER_ONLY) {
            final var contract = requireNonNull(contractFrom(context));
            final var config = context.configuration();
            contractGetInfo.contractInfo(infoFor(
                    contract,
                    config.getConfigData(TokensConfig.class),
                    config.getConfigData(LedgerConfig.class),
                    config.getConfigData(StakingConfig.class),
                    context.createStore(ReadableTokenStore.class),
                    context.createStore(ReadableStakingInfoStore.class),
                    context.createStore(ReadableTokenRelationStore.class),
                    context.createStore(ReadableNetworkStakingRewardsStore.class)));
        }
        return Response.newBuilder().contractGetInfo(contractGetInfo).build();
    }

    private ContractInfo infoFor(
            @NonNull final Account contract,
            @NonNull final TokensConfig tokensConfig,
            @NonNull final LedgerConfig ledgerConfig,
            @NonNull final StakingConfig stakingConfig,
            @NonNull final ReadableTokenStore tokenStore,
            @NonNull final ReadableStakingInfoStore stakingInfoStore,
            @NonNull final ReadableTokenRelationStore tokenRelationStore,
            @NonNull final ReadableNetworkStakingRewardsStore stakingRewardsStore) {
        final var accountId = contract.accountIdOrThrow();
        final var stakingInfo = summarizeStakingInfo(
                stakingConfig.rewardHistoryNumStoredPeriods(),
                stakingConfig.periodMins(),
                stakingRewardsStore.isStakingRewardsActivated(),
                contract,
                stakingInfoStore);
        final var maxReturnedRels = tokensConfig.maxRelsPerInfoQuery();
        return ContractInfo.newBuilder()
                .ledgerId(ledgerConfig.id())
                .accountID(accountId)
                .contractID(ContractID.newBuilder().contractNum(accountId.accountNumOrThrow()))
                .deleted(contract.deleted())
                .memo(contract.memo())
                .adminKey(contract.keyOrThrow())
                .storage(contract.contractKvPairsNumber() * BYTES_PER_EVM_KEY_VALUE_PAIR)
                .autoRenewPeriod(Duration.newBuilder().seconds(contract.autoRenewSeconds()))
                .autoRenewAccountId(contract.autoRenewAccountId())
                .balance(contract.tinybarBalance())
                .expirationTime(Timestamp.newBuilder().seconds(contract.expirationSecond()))
                .maxAutomaticTokenAssociations(contract.maxAutoAssociations())
                .contractAccountID(hexedEvmAddressOf(contract))
                .tokenRelationships(tokenRelationshipsOf(contract, tokenStore, tokenRelationStore, maxReturnedRels))
                .stakingInfo(stakingInfo)
                .build();
    }

    private @Nullable Account contractFrom(@NonNull final QueryContext context) {
        final var accountsStore = context.createStore(ReadableAccountStore.class);
        final var contractId = context.query().contractGetInfoOrThrow().contractIDOrElse(ContractID.DEFAULT);
        final var contract = accountsStore.getContractById(contractId);
        return (contract == null || !contract.smartContract()) ? null : contract;
    }

    @NonNull
    @Override
    public Fees computeFees(@NonNull final QueryContext context) {
        return context.feeCalculator().calculate();
    }
}
