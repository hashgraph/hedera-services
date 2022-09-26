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
package com.hedera.services.ledger.accounts;

import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.AUTO_RENEW_ACCOUNT_ID;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.AUTO_RENEW_PERIOD;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.DECLINE_REWARD;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.EXPIRY;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.IS_DELETED;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.IS_RECEIVER_SIG_REQUIRED;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.IS_SMART_CONTRACT;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.KEY;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.MAX_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.MEMO;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.PROXY;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.STAKED_ID;
import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.USED_AUTOMATIC_ASSOCIATIONS;
import static com.hedera.services.ledger.properties.TestAccountProperty.FLAG;
import static com.hedera.services.ledger.properties.TestAccountProperty.LONG;
import static com.hedera.services.ledger.properties.TestAccountProperty.OBJ;

import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.ledger.properties.TestAccountProperty;
import java.util.HashMap;
import java.util.Map;

public final class TestAccountCustomizer
        extends AccountCustomizer<Long, TestAccount, TestAccountProperty, TestAccountCustomizer> {
    protected static final Map<Option, TestAccountProperty> OPTION_PROPERTIES = new HashMap<>();

    static {
        OPTION_PROPERTIES.put(KEY, OBJ);
        OPTION_PROPERTIES.put(MEMO, OBJ);
        OPTION_PROPERTIES.put(PROXY, OBJ);
        OPTION_PROPERTIES.put(EXPIRY, LONG);
        OPTION_PROPERTIES.put(IS_DELETED, FLAG);
        OPTION_PROPERTIES.put(AUTO_RENEW_PERIOD, LONG);
        OPTION_PROPERTIES.put(IS_SMART_CONTRACT, FLAG);
        OPTION_PROPERTIES.put(IS_RECEIVER_SIG_REQUIRED, FLAG);
        OPTION_PROPERTIES.put(MAX_AUTOMATIC_ASSOCIATIONS, LONG);
        OPTION_PROPERTIES.put(USED_AUTOMATIC_ASSOCIATIONS, LONG);
        OPTION_PROPERTIES.put(AUTO_RENEW_ACCOUNT_ID, OBJ);
        OPTION_PROPERTIES.put(DECLINE_REWARD, FLAG);
        OPTION_PROPERTIES.put(STAKED_ID, OBJ);
    }

    public TestAccountCustomizer(
            final ChangeSummaryManager<TestAccount, TestAccountProperty> changeManager) {
        super(TestAccountProperty.class, OPTION_PROPERTIES, changeManager);
    }

    @Override
    protected TestAccountCustomizer self() {
        return this;
    }
}
