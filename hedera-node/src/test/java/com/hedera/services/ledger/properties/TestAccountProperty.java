package com.hedera.services.ledger.properties;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.ledger.accounts.TestAccount;
import com.hedera.services.tokens.TokenScope;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public enum TestAccountProperty implements BeanProperty<TestAccount> {
	FLAG {
		@Override
		public BiConsumer<TestAccount, Object> setter() {
			return (a, f) -> a.setFlag((boolean)f);
		}

		@Override
		public Function<TestAccount, Object> getter() {
			return TestAccount::isFlag;
		}
	},
	LONG {
		@Override
		public BiConsumer<TestAccount, Object> setter() {
			return (a, v) -> a.setValue((long)v);
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
	TOKEN {
		@Override
		public Function<TestAccount, Object> getter() {
			throw new UnsupportedOperationException("Token property requires a scope!");
		}

		@Override
		public BiFunction<TestAccount, TokenScope, Object> scopedGetter() {
			return (a, s) -> a.getTokenThing() - s.id().getTokenNum();
		}

		@Override
		public BiConsumer<TestAccount, Object> setter() {
			return (a, v) -> {
				if (v instanceof TokenScopedPropertyValue) {
					var sv = (TokenScopedPropertyValue)v;
					a.setTokenThing((long)sv.value() + sv.id().getTokenNum());
				} else {
					throw new IllegalArgumentException("Can only set token-scoped values!");
				}
			};
		}
	}
}
