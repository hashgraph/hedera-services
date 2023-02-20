/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.persistence;

import static com.hedera.services.bdd.spec.persistence.Entity.UNUSED_KEY;
import static com.hedera.services.bdd.spec.persistence.SpecKey.RegistryForms.under;
import static com.hedera.services.bdd.spec.persistence.SpecKey.adminKeyFor;
import static com.hedera.services.bdd.spec.persistence.SpecKey.submitKeyFor;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.consensus.HapiTopicCreate;
import java.util.Optional;

public class Topic {
    private static final String UNUSED_ACCOUNT = null;

    private String autoRenewAccount = UNUSED_ACCOUNT;
    private SpecKey adminKey = UNUSED_KEY;
    private SpecKey submitKey = UNUSED_KEY;

    public void registerWhatIsKnown(HapiSpec spec, String name, Optional<EntityId> entityId) {
        if (adminKey != UNUSED_KEY) {
            adminKey.registerWith(spec, under(adminKeyFor(name)));
        }
        if (submitKey != UNUSED_KEY) {
            submitKey.registerWith(spec, under(submitKeyFor(name)));
        }
        entityId.ifPresent(id -> {
            spec.registry().saveTopicId(name, id.asTopic());
        });
    }

    public HapiQueryOp<?> existenceCheck(String name) {
        return getTopicInfo(name);
    }

    HapiTxnOp<HapiTopicCreate> createOp(String name) {
        var op = createTopic(name).advertisingCreation();

        if (adminKey != UNUSED_KEY) {
            op.adminKeyName(adminKeyFor(name));
        }
        if (submitKey != UNUSED_KEY) {
            op.submitKeyName(submitKeyFor(name));
        }
        if (autoRenewAccount != UNUSED_ACCOUNT) {
            op.autoRenewAccountId(autoRenewAccount);
        }

        return op;
    }

    public SpecKey getAdminKey() {
        return adminKey;
    }

    public void setAdminKey(SpecKey adminKey) {
        this.adminKey = adminKey;
    }

    public SpecKey getSubmitKey() {
        return submitKey;
    }

    public void setSubmitKey(SpecKey submitKey) {
        this.submitKey = submitKey;
    }

    public String getAutoRenewAccount() {
        return autoRenewAccount;
    }

    public void setAutoRenewAccount(String autoRenewAccount) {
        this.autoRenewAccount = autoRenewAccount;
    }
}
