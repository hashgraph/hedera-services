/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package token;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import common.AbstractXTest;
import common.BaseScaffoldingComponent;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;

public abstract class AbstractTokenUpdateXTest extends AbstractXTest {

    protected static final String TOKEN_TREASURY = "tokenTreasury";

    protected static final AccountID DEFAULT_PAYER_ID =
            AccountID.newBuilder().accountNum(2L).build();

    protected TokenScaffoldingComponent component;

    @BeforeEach
    void setUp() {
        component = DaggerTokenScaffoldingComponent.factory().create(metrics, configuration());
    }

    protected Configuration configuration() {
        return HederaTestConfigBuilder.create().getOrCreateConfig();
    }

    @Override
    protected BaseScaffoldingComponent component() {
        return component;
    }

    protected Map<AccountID, Account> initialAccounts() {
        final var accounts = super.initialAccounts();
        addNamedAccount(TOKEN_TREASURY, accounts);
        return accounts;
    }
}
