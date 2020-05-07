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

import com.hedera.services.context.domain.haccount.HederaAccount;
import com.hedera.services.legacy.core.jproto.JAccountID;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JTransactionRecord;
import com.hedera.services.legacy.exception.NegativeAccountBalanceException;
import com.swirlds.fcqueue.FCQueue;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Implements a property family whose instances can provide the
 * getter/setter pairs relevant to themselves on a {@link HederaAccount} object.
 *
 * @author Michael Tinker
 */
@SuppressWarnings("unchecked")
public enum MapValueProperty implements BeanProperty<HederaAccount> {
	IS_DELETED {
		@Override
		public BiConsumer<HederaAccount, Object> setter() {
			return (a, f) -> a.setDeleted((boolean)f);
		}

		@Override
		public Function<HederaAccount, Object> getter() {
			return HederaAccount::isDeleted;
		}
	},
	IS_RECEIVER_SIG_REQUIRED {
		@Override
		public BiConsumer<HederaAccount, Object> setter() {
			return (a, f) -> a.setReceiverSigRequired((boolean)f);
		}

		@Override
		public Function<HederaAccount, Object> getter() {
			return HederaAccount::isReceiverSigRequired;
		}
	},
	IS_SMART_CONTRACT {
		@Override
		public BiConsumer<HederaAccount, Object> setter() {
			return (a, f) -> a.setSmartContract((boolean)f);
		}

		@Override
		public Function<HederaAccount, Object> getter() {
			return HederaAccount::isSmartContract;
		}
	},
	BALANCE {
		@Override
		public BiConsumer<HederaAccount, Object> setter() {
			return (a, v) -> {
				try {
					a.setBalance((long)v);
				} catch (NegativeAccountBalanceException nabe) {
					throw new IllegalArgumentException("Account balances must be nonnegative!");
				}
			};
		}

		@Override
		public Function<HederaAccount, Object> getter() {
			return HederaAccount::getBalance;
		}
	},
	FUNDS_RECEIVED_RECORD_THRESHOLD {
		@Override
		public BiConsumer<HederaAccount, Object> setter() {
			return (a, v) -> a.setReceiverThreshold((long)v);
		}

		@Override
		public Function<HederaAccount, Object> getter() {
			return HederaAccount::getReceiverThreshold;
		}
	},
	FUNDS_SENT_RECORD_THRESHOLD {
		@Override
		public BiConsumer<HederaAccount, Object> setter() {
			return (a, v) -> a.setSenderThreshold((long)v);
		}

		@Override
		public Function<HederaAccount, Object> getter() {
			return HederaAccount::getSenderThreshold;
		}
	},
	AUTO_RENEW_PERIOD {
		@Override
		public BiConsumer<HederaAccount, Object> setter() {
			return (a, v) -> a.setAutoRenewPeriod((long)v);
		}

		@Override
		public Function<HederaAccount, Object> getter() {
			return HederaAccount::getAutoRenewPeriod;
		}
	},
	EXPIRY {
		@Override
		public BiConsumer<HederaAccount, Object> setter() {
			return (a, v) -> a.setExpirationTime((long)v);
		}

		@Override
		public Function<HederaAccount, Object> getter() {
			return HederaAccount::getExpirationTime;
		}
	},
	KEY {
		@Override
		public BiConsumer<HederaAccount, Object> setter() {
			return (a, k) -> a.setAccountKeys((JKey)k);
		}

		@Override
		public Function<HederaAccount, Object> getter() {
			return HederaAccount::getAccountKeys;
		}
	},
	MEMO {
		@Override
		public BiConsumer<HederaAccount, Object> setter() {
			return (a, s) -> a.setMemo((String)s);
		}

		@Override
		public Function<HederaAccount, Object> getter() {
			return HederaAccount::getMemo;
		}
	},
	PROXY {
		@Override
		public BiConsumer<HederaAccount, Object> setter() {
			return (a, p) -> a.setProxyAccount((JAccountID)p);
		}

		@Override
		public Function<HederaAccount, Object> getter() {
			return HederaAccount::getProxyAccount;
		}
	},
	TRANSACTION_RECORDS {
		@Override
		public BiConsumer<HederaAccount, Object> setter() {
			return (a, r) -> a.setRecords((FCQueue<JTransactionRecord>)r);
		}

		@Override
		public Function<HederaAccount, Object> getter() {
			return a -> a.getRecords().copy(true);
		}
	};

	@Override
	abstract public BiConsumer<HederaAccount, Object> setter();
	@Override
	abstract public Function<HederaAccount, Object> getter();
}
