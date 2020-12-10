package com.hedera.services.store;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Duration;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

public abstract class HederaStore {
    protected final EntityIdSource ids;

    protected HederaLedger hederaLedger;
    protected TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger;

    protected HederaStore(
            EntityIdSource ids
    ) {
        this.ids = ids;
    }

    protected void setAccountsLedger(TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger) {
        this.accountsLedger = accountsLedger;
    }

    protected void setHederaLedger(HederaLedger hederaLedger) {
        this.hederaLedger = hederaLedger;
    }

    public void rollbackCreation() {
        ids.reclaimLastId();
    }

    protected ResponseCodeEnum accountCheck(AccountID id, ResponseCodeEnum failure) {
        if (!accountsLedger.exists(id) || (boolean) accountsLedger.get(id, AccountProperty.IS_DELETED)) {
            return failure;
        }
        return OK;
    }

    protected ResponseCodeEnum checkAccountExistence(AccountID aId) {
        return accountsLedger.exists(aId)
                ? (hederaLedger.isDeleted(aId) ? ACCOUNT_DELETED : OK)
                : INVALID_ACCOUNT_ID;
    }
}
