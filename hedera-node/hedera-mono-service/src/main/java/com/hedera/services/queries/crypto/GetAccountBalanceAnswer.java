/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.queries.crypto;

import static com.hedera.services.context.primitives.StateView.doBoundedIteration;
import static com.hedera.services.utils.EntityIdUtils.asAccount;
import static com.hedera.services.utils.EntityIdUtils.isAlias;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetAccountBalance;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.queries.AnswerService;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.CryptoGetAccountBalanceQuery;
import com.hederahashgraph.api.proto.java.CryptoGetAccountBalanceResponse;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenBalance;
import com.swirlds.merkle.map.MerkleMap;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class GetAccountBalanceAnswer implements AnswerService {
    private final AliasManager aliasManager;
    private final OptionValidator optionValidator;
    private final GlobalDynamicProperties dynamicProperties;

    @Inject
    public GetAccountBalanceAnswer(
            final AliasManager aliasManager,
            final OptionValidator optionValidator,
            final GlobalDynamicProperties dynamicProperties) {
        this.aliasManager = aliasManager;
        this.optionValidator = optionValidator;
        this.dynamicProperties = dynamicProperties;
    }

    @Override
    public ResponseCodeEnum checkValidity(Query query, StateView view) {
        MerkleMap<EntityNum, MerkleAccount> accounts = view.accounts();
        CryptoGetAccountBalanceQuery op = query.getCryptogetAccountBalance();
        return validityOf(op, accounts);
    }

    @Override
    public boolean requiresNodePayment(Query query) {
        return false;
    }

    @Override
    public boolean needsAnswerOnlyCost(Query query) {
        return false;
    }

    @Override
    public Response responseGiven(
            Query query, @Nullable StateView view, ResponseCodeEnum validity, long cost) {
        CryptoGetAccountBalanceQuery op = query.getCryptogetAccountBalance();

        final var id = targetOf(op);
        CryptoGetAccountBalanceResponse.Builder opAnswer =
                CryptoGetAccountBalanceResponse.newBuilder()
                        .setHeader(answerOnlyHeader(validity))
                        .setAccountID(id);

        if (validity == OK) {
            final var accounts = Objects.requireNonNull(view).accounts();
            final var key = EntityNum.fromAccountId(id);
            final var account = accounts.get(key);
            opAnswer.setBalance(account.getBalance());
            final var maxRels = dynamicProperties.maxTokensRelsPerInfoQuery();
            final var firstRel = account.getLatestAssociation();
            doBoundedIteration(
                    view.tokenAssociations(),
                    view.tokens(),
                    firstRel,
                    maxRels,
                    (token, rel) ->
                            opAnswer.addTokenBalances(
                                    TokenBalance.newBuilder()
                                            .setTokenId(token.grpcId())
                                            .setDecimals(token.decimals())
                                            .setBalance(rel.getBalance())
                                            .build()));
        }

        return Response.newBuilder().setCryptogetAccountBalance(opAnswer).build();
    }

    @Override
    public Optional<SignedTxnAccessor> extractPaymentFrom(Query query) {
        return Optional.empty();
    }

    private ResponseCodeEnum validityOf(
            final CryptoGetAccountBalanceQuery op,
            final MerkleMap<EntityNum, MerkleAccount> accounts) {
        if (op.hasContractID()) {
            final var effId = resolvedContract(op.getContractID());
            return optionValidator.queryableContractStatus(effId, accounts);
        } else if (op.hasAccountID()) {
            final var effId = resolvedNonContract(op.getAccountID());
            return optionValidator.queryableAccountStatus(effId, accounts);
        } else {
            return INVALID_ACCOUNT_ID;
        }
    }

    private AccountID targetOf(final CryptoGetAccountBalanceQuery op) {
        if (op.hasContractID()) {
            return asAccount(resolvedContract(op.getContractID()));
        } else {
            return resolvedNonContract(op.getAccountID());
        }
    }

    private AccountID resolvedNonContract(final AccountID idOrAlias) {
        if (isAlias(idOrAlias)) {
            final var id = aliasManager.lookupIdBy(idOrAlias.getAlias());
            return id.toGrpcAccountId();
        } else {
            return idOrAlias;
        }
    }

    private ContractID resolvedContract(final ContractID idOrAlias) {
        if (isAlias(idOrAlias)) {
            final var id = aliasManager.lookupIdBy(idOrAlias.getEvmAddress());
            return id.toGrpcContractID();
        } else {
            return idOrAlias;
        }
    }

    @Override
    public ResponseCodeEnum extractValidityFrom(Response response) {
        return response.getCryptogetAccountBalance().getHeader().getNodeTransactionPrecheckCode();
    }

    @Override
    public HederaFunctionality canonicalFunction() {
        return CryptoGetAccountBalance;
    }
}
