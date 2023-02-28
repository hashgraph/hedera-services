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

package com.hedera.services.yahcli.suites;

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.scheduleCreate;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoUpdate;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hederahashgraph.api.proto.java.*;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class UpdateSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(UpdateSuite.class);

    private final Map<String, String> specConfig;
    private final String memo;
    private final List<Key> keys;
    private final boolean schedule;
    private final String targetAccount;

    public UpdateSuite(
            final Map<String, String> specConfig,
            final String memo,
            final List<Key> keys,
            final String targetAccount,
            final boolean schedule) {
        this.memo = memo;
        this.specConfig = specConfig;
        this.keys = keys;
        this.targetAccount = targetAccount;
        this.schedule = schedule;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(doUpdate());
    }

    private HapiSpec doUpdate() {
        Key newList = Key.newBuilder()
                .setKeyList(KeyList.newBuilder().addAllKeys(keys))
                .build();
        HapiTxnOp<?> update = new HapiCryptoUpdate(DEFAULT_SHARD_REALM + targetAccount)
                .signedBy(DEFAULT_PAYER)
                .protoKey(newList)
                .blankMemo()
                .entityMemo(memo);

        // flag that transferred as parameter to schedule a key change or to execute right away
        if (schedule) {
            update = scheduleCreate("update", update).logged();
        }

        return HapiSpec.customHapiSpec("DoUpdate")
                .withProperties(specConfig)
                .given()
                .when()
                .then(update);
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
