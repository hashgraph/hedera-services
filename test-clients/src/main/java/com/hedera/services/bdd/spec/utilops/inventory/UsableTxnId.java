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
package com.hedera.services.bdd.spec.utilops.inventory;

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class UsableTxnId extends UtilOp {
    static final Logger log = LogManager.getLogger(UsableTxnId.class);

    private boolean useScheduledInappropriately = false;
    private Optional<String> payerId = Optional.empty();
    private final String name;

    public UsableTxnId(String name) {
        this.name = name;
    }

    public UsableTxnId payerId(String id) {
        payerId = Optional.of(id);
        return this;
    }

    public UsableTxnId settingScheduledInappropriately() {
        useScheduledInappropriately = true;
        return this;
    }

    @Override
    protected boolean submitOp(HapiSpec spec) {
        TransactionBody.Builder usable = TransactionBody.newBuilder();
        spec.txns().defaultBodySpec().accept(usable);
        if (payerId.isPresent()) {
            String s = payerId.get();
            AccountID id =
                    TxnUtils.isIdLiteral(s)
                            ? HapiPropertySource.asAccount(s)
                            : spec.registry().getAccountID(s);
            usable.setTransactionID(usable.getTransactionIDBuilder().setAccountID(id));
        }
        if (useScheduledInappropriately) {
            usable.setTransactionID(usable.getTransactionIDBuilder().setScheduled(true));
        }
        spec.registry().saveTxnId(name, usable.build().getTransactionID());
        return false;
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        payerId.ifPresent(id -> super.toStringHelper().add("id", id));
        return super.toStringHelper().add("name", name);
    }
}
