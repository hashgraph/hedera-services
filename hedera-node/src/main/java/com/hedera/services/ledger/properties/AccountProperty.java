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

import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.exceptions.NegativeAccountBalanceException;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleAccountTokens;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.submerkle.ExpirableTxnRecord;
import com.swirlds.fcqueue.FCQueue;

import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Implements a property family whose instances can provide the
 * getter/setter pairs relevant to themselves on a {@link MerkleAccount} object.
 *
 * @author Michael Tinker
 */
@SuppressWarnings("unchecked")
public enum AccountProperty implements BeanProperty<MerkleAccount> {
	IS_DELETED {
		@Override
		public BiConsumer<MerkleAccount, Object> setter() {
			return (a, f) -> a.setDeleted((boolean) f);
		}

		@Override
		public Function<MerkleAccount, Object> getter() {
			return MerkleAccount::isDeleted;
		}
	},
	IS_RECEIVER_SIG_REQUIRED {
		@Override
		public BiConsumer<MerkleAccount, Object> setter() {
			return (a, f) -> a.setReceiverSigRequired((boolean) f);
		}

		@Override
		public Function<MerkleAccount, Object> getter() {
			return MerkleAccount::isReceiverSigRequired;
		}
	},
	IS_SMART_CONTRACT {
		@Override
		public BiConsumer<MerkleAccount, Object> setter() {
			return (a, f) -> a.setSmartContract((boolean) f);
		}

		@Override
		public Function<MerkleAccount, Object> getter() {
			return MerkleAccount::isSmartContract;
		}
	},
	BALANCE {
		@Override
		@SuppressWarnings("unchecked")
		public BiConsumer<MerkleAccount, Object> setter() {
			return (a, v) -> {
				try {
					a.setBalance((long) v);
				} catch (NegativeAccountBalanceException nabe) {
					throw new IllegalArgumentException("Account balances must be nonnegative!");
				}
			};
		}

		@Override
		public Function<MerkleAccount, Object> getter() {
			return MerkleAccount::getBalance;
		}
	},
	FUNDS_RECEIVED_RECORD_THRESHOLD {
		@Override
		public BiConsumer<MerkleAccount, Object> setter() {
			return (a, v) -> a.setReceiverThreshold((long) v);
		}

		@Override
		public Function<MerkleAccount, Object> getter() {
			return MerkleAccount::getReceiverThreshold;
		}
	},
	FUNDS_SENT_RECORD_THRESHOLD {
		@Override
		public BiConsumer<MerkleAccount, Object> setter() {
			return (a, v) -> a.setSenderThreshold((long) v);
		}

		@Override
		public Function<MerkleAccount, Object> getter() {
			return MerkleAccount::getSenderThreshold;
		}
	},
	AUTO_RENEW_PERIOD {
		@Override
		public BiConsumer<MerkleAccount, Object> setter() {
			return (a, v) -> a.setAutoRenewSecs((long) v);
		}

		@Override
		public Function<MerkleAccount, Object> getter() {
			return MerkleAccount::getAutoRenewSecs;
		}
	},
	EXPIRY {
		@Override
		public BiConsumer<MerkleAccount, Object> setter() {
			return (a, v) -> a.setExpiry((long) v);
		}

		@Override
		public Function<MerkleAccount, Object> getter() {
			return MerkleAccount::getExpiry;
		}
	},
	KEY {
		@Override
		public BiConsumer<MerkleAccount, Object> setter() {
			return (a, k) -> a.setKey((JKey) k);
		}

		@Override
		public Function<MerkleAccount, Object> getter() {
			return MerkleAccount::getKey;
		}
	},
	MEMO {
		@Override
		public BiConsumer<MerkleAccount, Object> setter() {
			return (a, s) -> a.setMemo((String) s);
		}

		@Override
		public Function<MerkleAccount, Object> getter() {
			return MerkleAccount::getMemo;
		}
	},
	PROXY {
		@Override
		public BiConsumer<MerkleAccount, Object> setter() {
			return (a, p) -> a.setProxy((EntityId) p);
		}

		@Override
		public Function<MerkleAccount, Object> getter() {
			return MerkleAccount::getProxy;
		}
	},
	TOKENS {
		@Override
		public BiConsumer<MerkleAccount, Object> setter() {
			return (a, t) -> a.setTokens((MerkleAccountTokens) t);
		}

		@Override
		public Function<MerkleAccount, Object> getter() {
			return MerkleAccount::tokens;
		}
	},
	PAYER_RECORDS {
		@Override
		public BiConsumer<MerkleAccount, Object> setter() {
			return (a, r) -> a.setPayerRecords((FCQueue<ExpirableTxnRecord>) r);
		}

		@Override
		public Function<MerkleAccount, Object> getter() {
			return MerkleAccount::payerRecords;
		}
	};

	@Override
	abstract public BiConsumer<MerkleAccount, Object> setter();

	@Override
	abstract public Function<MerkleAccount, Object> getter();
}
