package com.hedera.services.txns.token.validators;

import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenFreezeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TokenUnfreezeAccountTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import java.util.List;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_TOKEN_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public class UnFreezeLogic {
    private final TypedTokenStore tokenStore;
    private final AccountStore accountStore;

    @Inject
    public UnFreezeLogic(final TypedTokenStore tokenStore, final AccountStore accountStore) {
        this.tokenStore = tokenStore;
        this.accountStore = accountStore;
    }

    public void unFreeze(Id targetTokenId, Id targetAccountId) {
        /* --- Load the model objects --- */
        final var loadedToken = tokenStore.loadToken(targetTokenId);
        final var loadedAccount = accountStore.loadAccount(targetAccountId);
        final var tokenRelationship = tokenStore.loadTokenRelationship(loadedToken, loadedAccount);

        /* --- Do the business logic --- */
        tokenRelationship.changeFrozenState(false);

        /* --- Persist the updated models --- */
        tokenStore.commitTokenRelationships(List.of(tokenRelationship));

    }

    public boolean isFrozen(Token token, Account account) {
        return tokenStore.loadTokenRelationship(token, account).isFrozen();
    }

    public ResponseCodeEnum validate(TransactionBody txnBody) {
        TokenUnfreezeAccountTransactionBody op = txnBody.getTokenUnfreeze();

        if (!op.hasToken()) {
            return INVALID_TOKEN_ID;
        }

        if (!op.hasAccount()) {
            return INVALID_ACCOUNT_ID;
        }

        return OK;
    }
}
