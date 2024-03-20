package com.hedera.services.bdd.spec.infrastructure.providers.ops.hollow;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoDelete;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INSUFFICIENT_PAYER_BALANCE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.LIVE_HASH_NOT_FOUND;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.UNKNOWN;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.EntityNameProvider;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoDelete;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.Optional;

public class RandomAccountDeletionHollowTransfer implements OpProvider {

    private final EntityNameProvider<AccountID> hollowAccounts;

    private final EntityNameProvider<AccountID> accountsToDelete;


    private final ResponseCodeEnum[] permissiblePrechecks = standardPrechecksAnd(ACCOUNT_DELETED, INVALID_ACCOUNT_ID);
    private final ResponseCodeEnum[] permissibleOutcomes = {
            SUCCESS, LIVE_HASH_NOT_FOUND, INSUFFICIENT_PAYER_BALANCE, UNKNOWN, ACCOUNT_DELETED, INVALID_ACCOUNT_ID};

    public RandomAccountDeletionHollowTransfer(EntityNameProvider<AccountID> hollowAccounts,
            EntityNameProvider<AccountID> accountsToDelete) {
        this.hollowAccounts = hollowAccounts;
        this.accountsToDelete = accountsToDelete;
    }

    @Override
    public Optional<HapiSpecOperation> get() {
        final var hollow = hollowAccounts.getQualifying();
        if (hollow.isEmpty()) {
            return Optional.empty();
        }

        final var target = accountsToDelete.getQualifying();
        if (target.isEmpty()) {
            return Optional.empty();
        }

        HapiCryptoDelete op = cryptoDelete(target.get())
                .purging()
                .transfer(hollow.get())
                .hasPrecheckFrom(permissiblePrechecks)
                .hasKnownStatusFrom(permissibleOutcomes);
        return Optional.of(op);
    }
}
