package com.hedera.services.files.interceptors;

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

import com.hedera.services.config.AccountNumbers;
import com.hedera.services.config.FileNumbers;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.files.FileUpdateInterceptor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hedera.services.legacy.core.jproto.JFileInfo;

import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Function;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.AUTHORIZATION_FAILED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ENTITY_NOT_ALLOWED_TO_DELETE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

public class TxnAwareAuthPolicy implements FileUpdateInterceptor {
	private final FileNumbers fileNums;
	private final AccountNumbers accountNums;
	private final PropertySource properties;
	private final TransactionContext txnCtx;

	private static final int APPLICABLE_PRIORITY = Integer.MIN_VALUE;

	static final Map.Entry<ResponseCodeEnum, Boolean> NO_DELETE_VERDICT =
			new AbstractMap.SimpleImmutableEntry<>(ENTITY_NOT_ALLOWED_TO_DELETE, false);
	static final Map.Entry<ResponseCodeEnum, Boolean> UNAUTHORIZED_VERDICT =
			new AbstractMap.SimpleImmutableEntry<>(AUTHORIZATION_FAILED, false);
	static final Map.Entry<ResponseCodeEnum, Boolean> YES_VERDICT =
			new AbstractMap.SimpleImmutableEntry<>(SUCCESS, true);

	private boolean initialized = false;
	private long numReservedSystemEntities;
	private Set<Long> systemAdminScope;
	private Set<Long> addressBookAdminScope;
	private Set<Long> feeSchedulesAdminScope;
	private Set<Long> exchangeRatesAdminScope;

	private final Function<FileID, Map.Entry<ResponseCodeEnum, Boolean>> defaultDecision =
			ignore -> UNAUTHORIZED_VERDICT;
	private Map<AccountID, Function<FileID, Map.Entry<ResponseCodeEnum, Boolean>>> accountDecisions;

	public TxnAwareAuthPolicy(
			FileNumbers fileNums,
			AccountNumbers accountNums,
			PropertySource properties,
			TransactionContext txnCtx
	) {
		this.txnCtx = txnCtx;
		this.fileNums = fileNums;
		this.properties = properties;
		this.accountNums = accountNums;
	}

	@Override
	public OptionalInt priorityForCandidate(FileID id) {
		lazyInitIfNecessary();

		var num = id.getFileNum();
		if (num <= numReservedSystemEntities) {
			return OptionalInt.of(APPLICABLE_PRIORITY);
		} else {
			return OptionalInt.empty();
		}
	}

	@Override
	public Map.Entry<ResponseCodeEnum, Boolean> preUpdate(FileID id, byte[] ignore) {
		return mutationVerdict(id);
	}

	/**
	 * This has to be a NoOp method. Ignore sonarCube suggestion to make it UnsupportedOperationException
	 * @param id the file that was updated
	 * @param contents the new contents of the file
	 */
	@Override
	public void postUpdate(FileID id, byte[] contents) {}

	@Override
	public Map.Entry<ResponseCodeEnum, Boolean> preDelete(FileID id) {
		lazyInitIfNecessary();

		if (priorityForCandidate(id).isPresent()) {
			return NO_DELETE_VERDICT;
		} else {
			return YES_VERDICT;
		}
	}

	@Override
	public Map.Entry<ResponseCodeEnum, Boolean> preAttrChange(FileID id, JFileInfo ignore) {
		return mutationVerdict(id);
	}

	private Map.Entry<ResponseCodeEnum, Boolean> mutationVerdict(FileID id) {
		lazyInitIfNecessary();

		if (priorityForCandidate(id).isPresent()) {
			return relevantDecision().apply(id);
		} else {
			return YES_VERDICT;
		}
	}

	private void lazyInitIfNecessary() {
		if (!initialized) {
			setDistinguishedNumbers();
			setScopes();

			accountDecisions = new HashMap<>();
			setTreasuryDecision();
			setSystemAdminDecision();
			setAdminDecisions();

			initialized = true;
		}
	}

	private void setScopes() {
		addressBookAdminScope = Set.of(
				fileNums.addressBook(),
				fileNums.nodeDetails(),
				fileNums.applicationProperties(),
				fileNums.apiPermissions());
		feeSchedulesAdminScope = Set.of(
				fileNums.feeSchedules());
		exchangeRatesAdminScope = Set.of(
				fileNums.exchangeRates(),
				fileNums.applicationProperties(),
				fileNums.apiPermissions());
		systemAdminScope = Set.of(
				fileNums.addressBook(),
				fileNums.nodeDetails(),
				fileNums.applicationProperties(),
				fileNums.exchangeRates(),
				fileNums.feeSchedules(),
				fileNums.apiPermissions());
	}

	private void setDistinguishedNumbers() {
		numReservedSystemEntities = properties.getLongProperty("hedera.numReservedSystemEntities");
	}

	private void setTreasuryDecision() {
		accountDecisions.put(accountWith(accountNums.treasury()), ignore -> YES_VERDICT);
	}

	private void setSystemAdminDecision() {
		accountDecisions.put(
				accountWith(accountNums.systemAdmin()),
				id -> systemAdminScope.contains(id.getFileNum()) ? YES_VERDICT : UNAUTHORIZED_VERDICT);
	}

	private void setAdminDecisions() {
		setScopedDecision(accountNums.addressBookAdmin(), addressBookAdminScope);
		setScopedDecision(accountNums.feeSchedulesAdmin(), feeSchedulesAdminScope);
		setScopedDecision(accountNums.exchangeRatesAdmin(), exchangeRatesAdminScope);
	}

	private void setScopedDecision(long idNum, Set<Long> scope) {
		accountDecisions.put(
				accountWith(idNum),
				id -> scope.contains(id.getFileNum()) ? YES_VERDICT : UNAUTHORIZED_VERDICT);
	}

	private AccountID accountWith(long num) {
		return AccountID.newBuilder().setAccountNum(num).build();
	}

	private Function<FileID, Map.Entry<ResponseCodeEnum, Boolean>> relevantDecision() {
		return accountDecisions.getOrDefault(txnCtx.activePayer(), defaultDecision);
	}
}
