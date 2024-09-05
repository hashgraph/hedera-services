/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import edu.umd.cs.findbugs.annotations.NonNull;
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
    protected boolean submitOp(@NonNull final HapiSpec spec) {
        final var txnId = spec.txns().nextTxnId().toBuilder();
        payerId.ifPresent(name -> txnId.setAccountID(TxnUtils.asId(name, spec)));
        if (useScheduledInappropriately) {
            txnId.setScheduled(true);
        }
        spec.registry().saveTxnId(name, txnId.build());
        return false;
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        payerId.ifPresent(id -> super.toStringHelper().add("id", id));
        return super.toStringHelper().add("name", name);
    }
}
