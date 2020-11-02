package com.hedera.services.ledger.accounts;

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

import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.BeanProperty;
import com.hedera.services.ledger.properties.ChangeSummaryManager;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.legacy.core.jproto.JKey;

import java.util.EnumMap;
import java.util.Map;

import static com.hedera.services.ledger.accounts.AccountCustomizer.Option.*;
import static java.util.Collections.unmodifiableMap;

/**
 * Implements a fluent builder for defining a set of standard customizations
 * relevant to any account on a ledger, no matter the id, account, and
 * property types.
 *
 * @param <K> the type of the id used by the ledger.
 * @param <A> the type of the account stored in the ledger.
 * @param <P> the type of the properties applicable to the account.
 * @param <T> the type of a customizer appropriate to {@code K}, {@code A}, {@code P}.
 *
 * @author Michael Tinker
 */
public abstract class AccountCustomizer<
		K,
		A,
		P extends Enum<P> & BeanProperty<A>, T extends AccountCustomizer<K, A, P, T>> {
	public enum Option {
		KEY,
		MEMO,
		PROXY,
		EXPIRY,
		IS_DELETED,
		IS_SMART_CONTRACT,
		AUTO_RENEW_PERIOD,
		IS_RECEIVER_SIG_REQUIRED,
	};

	private final Map<Option, P> optionProperties;
	private final EnumMap<P, Object> changes;
	private final ChangeSummaryManager<A, P> changeManager;

	protected abstract T self();

	public AccountCustomizer(
			Class<P> propertyType,
			Map<Option, P> optionProperties,
			ChangeSummaryManager<A, P> changeManager
	) {
		this.changeManager = changeManager;
		this.optionProperties = optionProperties;
		this.changes = new EnumMap<>(propertyType);
	}

	public EnumMap<P, Object> getChanges() {
		return changes;
	}

	public Map<Option, P> getOptionProperties() {
		return unmodifiableMap(optionProperties);
	}

	public A customizing(A account) {
		changeManager.persist(changes, account);
		return account;
	}

	public void customize(K id, TransactionalLedger<K, P, A> ledger) {
		changes.entrySet().forEach(change -> ledger.set(id, change.getKey(), change.getValue()));
	}

	public T key(JKey option) {
		changeManager.update(changes, optionProperties.get(KEY), option);
		return self();
	}

	public T memo(String option) {
		changeManager.update(changes, optionProperties.get(MEMO), option);
		return self();
	}

	public T proxy(EntityId option) {
		changeManager.update(changes, optionProperties.get(PROXY), option);
		return self();
	}

	public T expiry(long option) {
		changeManager.update(changes, optionProperties.get(EXPIRY), option);
		return self();
	}

	public T isDeleted(boolean option) {
		changeManager.update(changes, optionProperties.get(IS_DELETED), option);
		return self();
	}

	public T autoRenewPeriod(long option) {
		changeManager.update(changes, optionProperties.get(AUTO_RENEW_PERIOD), option);
		return self();
	}

	public T isSmartContract(boolean option) {
		changeManager.update(changes, optionProperties.get(IS_SMART_CONTRACT), option);
		return self();
	}

	public T isReceiverSigRequired(boolean option) {
		changeManager.update(changes, optionProperties.get(IS_RECEIVER_SIG_REQUIRED), option);
		return self();
	}
}
