package com.hedera.services.security.ops;

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

import com.hedera.services.config.EntityNumbers;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TransactionBody;

import java.util.EnumMap;
import java.util.Optional;
import java.util.function.Function;

import static com.hedera.services.security.ops.SystemOpAuthorization.AUTHORIZED;
import static com.hedera.services.security.ops.SystemOpAuthorization.IMPERMISSIBLE;
import static com.hedera.services.security.ops.SystemOpAuthorization.UNAUTHORIZED;
import static com.hedera.services.security.ops.SystemOpAuthorization.UNNECESSARY;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileAppend;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.Freeze;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.SystemDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.SystemUndelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.UncheckedSubmit;

public class SystemOpPolicies {
	private final EntityNumbers entityNums;

	private final EnumMap<HederaFunctionality, Function<TransactionBody, SystemOpAuthorization>> functionPolicies;

	public SystemOpPolicies(EntityNumbers entityNums) {
		this.entityNums = entityNums;

		functionPolicies = new EnumMap<>(HederaFunctionality.class);

		functionPolicies.put(FileDelete, this::checkFileDelete);
		functionPolicies.put(CryptoDelete, this::checkCryptoDelete);
		functionPolicies.put(ContractDelete, this::checkContractDelete);

		functionPolicies.put(CryptoUpdate, this::checkCryptoUpdate);
		functionPolicies.put(ContractUpdate, this::checkContractUpdate);
		functionPolicies.put(FileUpdate, this::checkFileUpdate);
		functionPolicies.put(FileAppend, this::checkFileAppend);

		functionPolicies.put(Freeze, this::checkFreeze);
		functionPolicies.put(SystemDelete, this::checkSystemDelete);
		functionPolicies.put(SystemUndelete, this::checkSystemUndelete);

		functionPolicies.put(UncheckedSubmit, this::checkUncheckedSubmit);
	}

	public SystemOpAuthorization check(SignedTxnAccessor accessor) {
		return check(accessor.getTxn(), accessor.getFunction());
	}

	public SystemOpAuthorization check(TransactionBody txn, HederaFunctionality function) {
		return Optional.ofNullable(functionPolicies.get(function))
				.map(opCheck -> opCheck.apply(txn))
				.orElse(UNNECESSARY);
	}

	private SystemOpAuthorization checkUncheckedSubmit(TransactionBody txn) {
		return entityNums.accounts().isSuperuser(payerFor(txn)) ? AUTHORIZED : UNAUTHORIZED;
	}

	private SystemOpAuthorization checkSystemUndelete(TransactionBody txn) {
		var op = txn.getSystemUndelete();
		long payer = payerFor(txn);
		if (op.hasFileID()) {
			return checkSystemUndeleteFile(payer, op.getFileID());
		} else {
			return checkSystemUndeleteContract(payer, op.getContractID());
		}
	}

	private SystemOpAuthorization checkSystemDelete(TransactionBody txn) {
		var op = txn.getSystemDelete();
		long payer = payerFor(txn);
		if (op.hasFileID()) {
			return checkSystemDeleteFile(payer, op.getFileID());
		} else {
			return checkSystemDeleteContract(payer, op.getContractID());
		}
	}

	private SystemOpAuthorization checkSystemUndeleteFile(long payerAccount, FileID id) {
		return entityNums.isSystemFile(id)
				? IMPERMISSIBLE
				: (hasSysUndelPrivileges(payerAccount) ? AUTHORIZED : UNAUTHORIZED);
	}

	private SystemOpAuthorization checkSystemUndeleteContract(long payerAccount, ContractID id) {
		return entityNums.isSystemContract(id)
				? IMPERMISSIBLE
				: (hasSysUndelPrivileges(payerAccount) ? AUTHORIZED : UNAUTHORIZED);
	}

	private SystemOpAuthorization checkSystemDeleteFile(long payerAccount, FileID id) {
		return entityNums.isSystemFile(id)
				? IMPERMISSIBLE
				: (hasSysDelPrivileges(payerAccount) ? AUTHORIZED : UNAUTHORIZED);
	}

	private SystemOpAuthorization checkSystemDeleteContract(long payerAccount, ContractID id) {
		return entityNums.isSystemContract(id)
				? IMPERMISSIBLE
				: (hasSysDelPrivileges(payerAccount) ? AUTHORIZED : UNAUTHORIZED);
	}

	private boolean hasSysDelPrivileges(long payerAccount) {
		return entityNums.accounts().isSuperuser(payerAccount) ||
				payerAccount == entityNums.accounts().systemDeleteAdmin();
	}

	private boolean hasSysUndelPrivileges(long payerAccount) {
		return entityNums.accounts().isSuperuser(payerAccount) ||
				payerAccount == entityNums.accounts().systemUndeleteAdmin();
	}

	private SystemOpAuthorization checkFreeze(TransactionBody txn) {
		var payer = payerFor(txn);
		boolean isAuthorized = payer == entityNums.accounts().treasury() ||
				payer == entityNums.accounts().systemAdmin() ||
				payer == entityNums.accounts().freezeAdmin();
		return isAuthorized ? AUTHORIZED : UNAUTHORIZED;
	}

	private SystemOpAuthorization checkContractUpdate(TransactionBody txn) {
		var target = txn.getContractUpdateInstance().getContractID();
		return entityNums.isSystemContract(target)
				? (canUpdate(payerFor(txn), target.getContractNum()) ? AUTHORIZED : UNAUTHORIZED)
				: UNNECESSARY;
	}

	private SystemOpAuthorization checkFileUpdate(TransactionBody txn) {
		var target = txn.getFileUpdate().getFileID();
		return entityNums.isSystemFile(target)
				? (canUpdate(payerFor(txn), target.getFileNum()) ? AUTHORIZED : UNAUTHORIZED)
				: UNNECESSARY;
	}

	private SystemOpAuthorization checkFileAppend(TransactionBody txn) {
		var target = txn.getFileAppend().getFileID();
		return entityNums.isSystemFile(target)
				? (canUpdate(payerFor(txn), target.getFileNum()) ? AUTHORIZED : UNAUTHORIZED)
				: UNNECESSARY;
	}

	private SystemOpAuthorization checkCryptoUpdate(TransactionBody txn) {
		var target = txn.getCryptoUpdateAccount().getAccountIDToUpdate();
		return entityNums.isSystemAccount(target)
				? (canUpdate(payerFor(txn), target.getAccountNum()) ? AUTHORIZED : UNAUTHORIZED)
				: UNNECESSARY;
	}

	private SystemOpAuthorization checkContractDelete(TransactionBody txn) {
		return entityNums.isSystemContract(txn.getContractDeleteInstance().getContractID())
				? IMPERMISSIBLE : UNNECESSARY;
	}

	private SystemOpAuthorization checkCryptoDelete(TransactionBody txn) {
		return entityNums.isSystemAccount(txn.getCryptoDelete().getDeleteAccountID())
				? IMPERMISSIBLE : UNNECESSARY;
	}

	private SystemOpAuthorization checkFileDelete(TransactionBody txn) {
		return entityNums.isSystemFile(txn.getFileDelete().getFileID())
				? IMPERMISSIBLE : UNNECESSARY;
	}

	private long payerFor(TransactionBody txn) {
		return txn.getTransactionID().getAccountID().getAccountNum();
	}

	boolean canUpdate(long payerAccount, long systemEntity) {
		if (payerAccount == entityNums.accounts().treasury()) {
			return true;
		} else if (payerAccount == entityNums.accounts().systemAdmin()) {
			return canSysAdminUpdate(systemEntity);
		} else if (payerAccount == systemEntity) {
			return true;
		} else if (payerAccount == entityNums.accounts().addressBookAdmin()) {
			return canAddressBookAdminUpdate(systemEntity);
		} else if (payerAccount == entityNums.accounts().exchangeRatesAdmin()) {
			return canExchangeRatesAdminUpdate(systemEntity);
		} else if (payerAccount == entityNums.accounts().feeSchedulesAdmin()) {
			return systemEntity == entityNums.files().feeSchedules();
		} else {
			return false;
		}
	}

	private boolean canExchangeRatesAdminUpdate(long entity) {
		return entity == entityNums.files().exchangeRates() || isPropertiesOrPermissions(entity);
	}

	private boolean canAddressBookAdminUpdate(long entity) {
		return entity == entityNums.files().addressBook() ||
				entity == entityNums.files().nodeDetails() ||
				isPropertiesOrPermissions(entity);
	}

	private boolean canSysAdminUpdate(long entity) {
		if (entity < entityNums.accounts().firstManagedBySysAdmin()) {
			return false;
		} else {
			if (entity <= entityNums.accounts().lastManagedBySysAdmin()) {
				return true;
			} else {
				return isNamedSystemFile(entity);
			}
		}
	}

	private boolean isNamedSystemFile(long entity) {
		return entity == entityNums.files().addressBook() ||
				entity == entityNums.files().nodeDetails() ||
				entity == entityNums.files().feeSchedules() ||
				entity == entityNums.files().exchangeRates() ||
				isPropertiesOrPermissions(entity);
	}

	private boolean isPropertiesOrPermissions(long entity) {
		return entity == entityNums.files().applicationProperties() ||
				entity == entityNums.files().apiPermissions();
	}
}
