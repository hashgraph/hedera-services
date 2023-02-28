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
import static com.hedera.services.bdd.spec.persistence.SpecKey.RegistryForms.asAdminKeyFor;
import static com.hedera.services.bdd.spec.persistence.SpecKey.adminKeyFor;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getScheduleInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hedera.services.bdd.spec.transactions.schedule.HapiScheduleCreate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class Schedule {
    static final String UNSPECIFIED_MEMO = null;
    static final SimpleXfer UNSPECIFIED_SIMPLE_XFER = null;

    private String memo = UNSPECIFIED_MEMO;
    private SpecKey adminKey = UNUSED_KEY;
    private SimpleXfer simpleXfer = UNSPECIFIED_SIMPLE_XFER;
    private List<String> signatories = Collections.emptyList();

    public void registerWhatIsKnown(HapiSpec spec, String name, Optional<EntityId> entityId) {
        if (adminKey != UNUSED_KEY) {
            adminKey.registerWith(spec, asAdminKeyFor(name));
        }
        entityId.ifPresent(id -> {
            spec.registry().saveScheduleId(name, id.asSchedule());
        });
    }

    public HapiQueryOp<?> existenceCheck(String name) {
        return getScheduleInfo(name);
    }

    HapiTxnOp<HapiScheduleCreate<HapiCryptoTransfer>> createOp(String name) {
        if (simpleXfer == UNSPECIFIED_SIMPLE_XFER) {
            simpleXfer = new SimpleXfer();
        }

        var op = scheduleCreate(
                        name,
                        cryptoTransfer(
                                tinyBarsFromTo(simpleXfer.getFrom(), simpleXfer.getTo(), simpleXfer.getAmount())))
                .alsoSigningWith(signatories.toArray(new String[0]))
                .advertisingCreation();

        if (adminKey != UNUSED_KEY) {
            op.adminKey(adminKeyFor(name));
        }
        if (memo != UNSPECIFIED_MEMO) {
            op.withEntityMemo(memo);
        }

        return op;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }

    public SpecKey getAdminKey() {
        return adminKey;
    }

    public void setAdminKey(SpecKey adminKey) {
        this.adminKey = adminKey;
    }

    public SimpleXfer getSimpleXfer() {
        return simpleXfer;
    }

    public void setSimpleXfer(SimpleXfer simpleXfer) {
        this.simpleXfer = simpleXfer;
    }

    public List<String> getSignatories() {
        return signatories;
    }

    public void setSignatories(List<String> signatories) {
        this.signatories = signatories;
    }
}
