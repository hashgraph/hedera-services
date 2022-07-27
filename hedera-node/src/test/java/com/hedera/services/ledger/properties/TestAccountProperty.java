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
package com.hedera.services.ledger.properties;

import com.hedera.services.ledger.accounts.TestAccount;
import java.util.function.BiConsumer;
import java.util.function.Function;

public enum TestAccountProperty implements BeanProperty<TestAccount> {
    FLAG {
        @Override
        public BiConsumer<TestAccount, Object> setter() {
            return (a, f) -> a.setFlag((boolean) f);
        }

        @Override
        public Function<TestAccount, Object> getter() {
            return TestAccount::isFlag;
        }
    },
    LONG {
        @Override
        public BiConsumer<TestAccount, Object> setter() {
            return (a, v) -> a.setValue((long) v);
        }

        @Override
        public Function<TestAccount, Object> getter() {
            return TestAccount::getValue;
        }
    },
    OBJ {
        @Override
        public BiConsumer<TestAccount, Object> setter() {
            return TestAccount::setThing;
        }

        @Override
        public Function<TestAccount, Object> getter() {
            return TestAccount::getThing;
        }
    },
    TOKEN_LONG {
        @Override
        public BiConsumer<TestAccount, Object> setter() {
            return (a, v) -> a.setTokenThing((long) v);
        }

        @Override
        public Function<TestAccount, Object> getter() {
            return TestAccount::getTokenThing;
        }
    },
    HBAR_ALLOWANCES {
        @Override
        public BiConsumer<TestAccount, Object> setter() {
            return (a, v) -> a.setValidHbarAllowances((TestAccount.Allowance) v);
        }

        @Override
        public Function<TestAccount, Object> getter() {
            return TestAccount::getValidHbarAllowances;
        }
    },
    FUNGIBLE_ALLOWANCES {
        @Override
        public BiConsumer<TestAccount, Object> setter() {
            return (a, v) -> a.setValidFungibleAllowances((TestAccount.Allowance) v);
        }

        @Override
        public Function<TestAccount, Object> getter() {
            return TestAccount::getValidFungibleAllowances;
        }
    },
    NFT_ALLOWANCES {
        @Override
        public BiConsumer<TestAccount, Object> setter() {
            return (a, v) -> a.setValidNftAllowances((TestAccount.Allowance) v);
        }

        @Override
        public Function<TestAccount, Object> getter() {
            return TestAccount::getValidNftAllowances;
        }
    }
}
