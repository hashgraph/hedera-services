package com.hedera.services.security.ops;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.config.MockEntityNumbers;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.ContractUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.FileAppendTransactionBody;
import com.hederahashgraph.api.proto.java.FileDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.FileUpdateTransactionBody;
import com.hederahashgraph.api.proto.java.FreezeTransactionBody;
import com.hederahashgraph.api.proto.java.SystemDeleteTransactionBody;
import com.hederahashgraph.api.proto.java.SystemUndeleteTransactionBody;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.UncheckedSubmitBody;
import org.junit.jupiter.api.Test;

import static com.hedera.services.security.ops.SystemOpAuthorization.AUTHORIZED;
import static com.hedera.services.security.ops.SystemOpAuthorization.IMPERMISSIBLE;
import static com.hedera.services.security.ops.SystemOpAuthorization.UNAUTHORIZED;
import static com.hedera.services.security.ops.SystemOpAuthorization.UNNECESSARY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemOpPoliciesTest {
	SystemOpPolicies subject = new SystemOpPolicies(new MockEntityNumbers());

	@Test
	public void treasuryCanUpdateAllNonAccountEntities() {
		// expect:
		assertTrue(subject.canPerformNonCryptoUpdate(2, 101));
		assertTrue(subject.canPerformNonCryptoUpdate(2, 102));
		assertTrue(subject.canPerformNonCryptoUpdate(2, 111));
		assertTrue(subject.canPerformNonCryptoUpdate(2, 112));
		assertTrue(subject.canPerformNonCryptoUpdate(2, 121));
		assertTrue(subject.canPerformNonCryptoUpdate(2, 122));
		assertTrue(subject.canPerformNonCryptoUpdate(2, 123));
		assertTrue(subject.canPerformNonCryptoUpdate(2, 150));
	}

	@Test
	public void sysAdminCanUpdateKnownSystemFiles() {
		// expect:
		assertTrue(subject.canPerformNonCryptoUpdate(50, 101));
		assertTrue(subject.canPerformNonCryptoUpdate(50, 102));
		assertTrue(subject.canPerformNonCryptoUpdate(50, 111));
		assertTrue(subject.canPerformNonCryptoUpdate(50, 112));
		assertTrue(subject.canPerformNonCryptoUpdate(50, 121));
		assertTrue(subject.canPerformNonCryptoUpdate(50, 122));
		assertTrue(subject.canPerformNonCryptoUpdate(50, 123));
		assertTrue(subject.canPerformNonCryptoUpdate(50, 150));
	}

	@Test
	public void addressBookAdminCanUpdateExpected() {
		// expect:
		assertTrue(subject.canPerformNonCryptoUpdate(55, 101));
		assertTrue(subject.canPerformNonCryptoUpdate(55, 102));
		assertTrue(subject.canPerformNonCryptoUpdate(55, 121));
		assertTrue(subject.canPerformNonCryptoUpdate(55, 122));
		assertTrue(subject.canPerformNonCryptoUpdate(55, 123));
		assertFalse(subject.canPerformNonCryptoUpdate(55, 111));
		assertFalse(subject.canPerformNonCryptoUpdate(55, 112));
		assertFalse(subject.canPerformNonCryptoUpdate(55, 150));
	}

	@Test
	public void feeSchedulesAdminCanUpdateExpected() {
		// expect:
		assertTrue(subject.canPerformNonCryptoUpdate(56, 111));
		assertFalse(subject.canPerformNonCryptoUpdate(56, 101));
		assertFalse(subject.canPerformNonCryptoUpdate(56, 102));
		assertFalse(subject.canPerformNonCryptoUpdate(56, 121));
		assertFalse(subject.canPerformNonCryptoUpdate(56, 122));
		assertFalse(subject.canPerformNonCryptoUpdate(56, 123));
		assertFalse(subject.canPerformNonCryptoUpdate(56, 112));
		assertFalse(subject.canPerformNonCryptoUpdate(56, 150));
	}

	@Test
	public void exchangeRatesAdminCanUpdateExpected() {
		// expect:
		assertTrue(subject.canPerformNonCryptoUpdate(57, 121));
		assertTrue(subject.canPerformNonCryptoUpdate(57, 122));
		assertTrue(subject.canPerformNonCryptoUpdate(57, 123));
		assertTrue(subject.canPerformNonCryptoUpdate(57, 112));
		assertFalse(subject.canPerformNonCryptoUpdate(57, 111));
		assertFalse(subject.canPerformNonCryptoUpdate(57, 101));
		assertFalse(subject.canPerformNonCryptoUpdate(57, 102));
		assertFalse(subject.canPerformNonCryptoUpdate(57, 150));
	}

	@Test
	public void uncheckedSubmitRejectsUnauthorized() throws InvalidProtocolBufferException {
		// given:
		var txn = civilianTxn()
				.setUncheckedSubmit(UncheckedSubmitBody
						.newBuilder()
						.setTransactionBytes(ByteString.copyFrom("DOESN'T MATTER".getBytes())));
		// expect:
		assertEquals(UNAUTHORIZED, subject.check(accessor(txn)));
	}

	@Test
	public void sysAdminCanSubmitUnchecked() throws InvalidProtocolBufferException {
		// given:
		var txn = sysAdminTxn()
				.setUncheckedSubmit(UncheckedSubmitBody
						.newBuilder()
						.setTransactionBytes(ByteString.copyFrom("DOESN'T MATTER".getBytes())));
		// expect:
		assertEquals(AUTHORIZED, subject.check(accessor(txn)));
	}

	@Test
	public void treasuryCanSubmitUnchecked() throws InvalidProtocolBufferException {
		// given:
		var txn = treasuryTxn()
				.setUncheckedSubmit(UncheckedSubmitBody
						.newBuilder()
						.setTransactionBytes(ByteString.copyFrom("DOESN'T MATTER".getBytes())));
		// expect:
		assertEquals(AUTHORIZED, subject.check(accessor(txn)));
	}

	@Test
	public void cryptoUpdateRecognizesAuthorized() throws InvalidProtocolBufferException {
		// given:
		var txn = treasuryTxn()
				.setCryptoUpdateAccount(CryptoUpdateTransactionBody
						.newBuilder()
						.setAccountIDToUpdate(account(75)));
		// expect:
		assertEquals(AUTHORIZED, subject.check(accessor(txn)));
	}

	@Test
	public void cryptoUpdateRecognizesUnnecessaryForSystem() throws InvalidProtocolBufferException {
		// given:
		var txn = civilianTxn()
				.setCryptoUpdateAccount(CryptoUpdateTransactionBody
						.newBuilder()
						.setAccountIDToUpdate(account(75)));
		// expect:
		assertEquals(UNNECESSARY, subject.check(accessor(txn)));
	}

	@Test
	public void cryptoUpdateRecognizesUnnecessaryForNonSystem() throws InvalidProtocolBufferException {
		// given:
		var txn = civilianTxn()
				.setCryptoUpdateAccount(CryptoUpdateTransactionBody
						.newBuilder()
						.setAccountIDToUpdate(account(1001)));
		// expect:
		assertEquals(UNNECESSARY, subject.check(accessor(txn)));
	}

	@Test
	public void cryptoUpdateRecognizesAuthorizedForTreasury() throws InvalidProtocolBufferException {
		// given:
		var selfUpdateTxn = treasuryTxn()
				.setCryptoUpdateAccount(CryptoUpdateTransactionBody
						.newBuilder()
						.setAccountIDToUpdate(account(2)));
		var otherUpdateTxn = treasuryTxn()
				.setCryptoUpdateAccount(CryptoUpdateTransactionBody
						.newBuilder()
						.setAccountIDToUpdate(account(50)));
		// expect:
		assertEquals(AUTHORIZED, subject.check(accessor(selfUpdateTxn)));
		assertEquals(AUTHORIZED, subject.check(accessor(otherUpdateTxn)));
	}

	@Test
	public void cryptoUpdateRecognizesUnauthorized() throws InvalidProtocolBufferException {
		// given:
		var civilianTxn = civilianTxn()
				.setCryptoUpdateAccount(CryptoUpdateTransactionBody
						.newBuilder()
						.setAccountIDToUpdate(account(2)));
		var sysAdminTxn = sysAdminTxn()
				.setCryptoUpdateAccount(CryptoUpdateTransactionBody
						.newBuilder()
						.setAccountIDToUpdate(account(2)));
		// expect:
		assertEquals(UNAUTHORIZED, subject.check(accessor(civilianTxn)));
		assertEquals(UNAUTHORIZED, subject.check(accessor(sysAdminTxn)));
	}

	@Test
	public void fileUpdateRecognizesUnauthorized() throws InvalidProtocolBufferException {
		// given:
		var txn = exchangeRatesAdminTxn()
				.setFileUpdate(FileUpdateTransactionBody
						.newBuilder()
						.setFileID(file(111)));
		// expect:
		assertEquals(UNAUTHORIZED, subject.check(accessor(txn)));
	}

	@Test
	public void fileAppendRecognizesUnauthorized() throws InvalidProtocolBufferException {
		// given:
		var txn = exchangeRatesAdminTxn()
				.setFileAppend(FileAppendTransactionBody
						.newBuilder()
						.setFileID(file(111)));
		// expect:
		assertEquals(UNAUTHORIZED, subject.check(accessor(txn)));
	}

	@Test
	public void fileAppendRecognizesAuthorized() throws InvalidProtocolBufferException {
		// given:
		var txn = exchangeRatesAdminTxn()
				.setFileAppend(FileAppendTransactionBody
						.newBuilder()
						.setFileID(file(112)));
		// expect:
		assertEquals(AUTHORIZED, subject.check(accessor(txn)));
	}

	@Test
	public void treasuryCanFreeze() throws InvalidProtocolBufferException {
		// given:
		var txn = treasuryTxn()
				.setFreeze(FreezeTransactionBody.getDefaultInstance());
		// expect:
		assertEquals(AUTHORIZED, subject.check(accessor(txn)));
	}

	@Test
	public void sysAdminCanFreeze() throws InvalidProtocolBufferException {
		// given:
		var txn = sysAdminTxn()
				.setFreeze(FreezeTransactionBody.getDefaultInstance());
		// expect:
		assertEquals(AUTHORIZED, subject.check(accessor(txn)));
	}

	@Test
	public void freezeAdminCanFreeze() throws InvalidProtocolBufferException {
		// given:
		var txn = freezeAdminTxn()
				.setFreeze(FreezeTransactionBody.getDefaultInstance());
		// expect:
		assertEquals(AUTHORIZED, subject.check(accessor(txn)));
	}

	@Test
	public void randomAdminCannotFreeze() throws InvalidProtocolBufferException {
		// given:
		var txn = exchangeRatesAdminTxn()
				.setFreeze(FreezeTransactionBody.getDefaultInstance());
		// expect:
		assertEquals(UNAUTHORIZED, subject.check(accessor(txn)));
	}

	@Test
	public void systemDeleteRecognizesImpermissibleContractDel() throws InvalidProtocolBufferException {
		// given:
		var txn = treasuryTxn()
				.setSystemDelete(SystemDeleteTransactionBody
						.newBuilder()
						.setContractID(contract(123)));
		// expect:
		assertEquals(IMPERMISSIBLE, subject.check(accessor(txn)));
	}

	@Test
	public void systemUndeleteRecognizesImpermissibleContractUndel() throws InvalidProtocolBufferException {
		// given:
		var txn = treasuryTxn()
				.setSystemUndelete(SystemUndeleteTransactionBody
						.newBuilder()
						.setContractID(contract(123)));
		// expect:
		assertEquals(IMPERMISSIBLE, subject.check(accessor(txn)));
	}

	@Test
	public void systemUndeleteRecognizesUnauthorizedContractUndel() throws InvalidProtocolBufferException {
		// given:
		var txn = exchangeRatesAdminTxn()
				.setSystemUndelete(SystemUndeleteTransactionBody
						.newBuilder()
						.setContractID(contract(1234)));
		// expect:
		assertEquals(UNAUTHORIZED, subject.check(accessor(txn)));
	}

	@Test
	public void systemUndeleteRecognizesAuthorizedContractUndel() throws InvalidProtocolBufferException {
		// given:
		var txn = sysUndeleteTxn()
				.setSystemUndelete(SystemUndeleteTransactionBody
						.newBuilder()
						.setContractID(contract(1234)));
		// expect:
		assertEquals(AUTHORIZED, subject.check(accessor(txn)));
	}

	@Test
	public void systemUndeleteRecognizesAuthorizedFileUndel() throws InvalidProtocolBufferException {
		// given:
		var txn = sysUndeleteTxn()
				.setSystemUndelete(SystemUndeleteTransactionBody
						.newBuilder()
						.setFileID(file(1234)));
		// expect:
		assertEquals(AUTHORIZED, subject.check(accessor(txn)));
	}

	@Test
	public void systemUndeleteRecognizesUnauthorizedFileUndel() throws InvalidProtocolBufferException {
		// given:
		var txn = exchangeRatesAdminTxn()
				.setSystemUndelete(SystemUndeleteTransactionBody
						.newBuilder()
						.setFileID(file(1234)));
		// expect:
		assertEquals(UNAUTHORIZED, subject.check(accessor(txn)));
	}

	@Test
	public void systemUndeleteRecognizesImpermissibleFileUndel() throws InvalidProtocolBufferException {
		// given:
		var txn = exchangeRatesAdminTxn()
				.setSystemUndelete(SystemUndeleteTransactionBody
						.newBuilder()
						.setFileID(file(123)));
		// expect:
		assertEquals(IMPERMISSIBLE, subject.check(accessor(txn)));
	}

	@Test
	public void systemDeleteRecognizesImpermissibleFileDel() throws InvalidProtocolBufferException {
		// given:
		var txn = treasuryTxn()
				.setSystemDelete(SystemDeleteTransactionBody
						.newBuilder()
						.setFileID(file(123)));
		// expect:
		assertEquals(IMPERMISSIBLE, subject.check(accessor(txn)));
	}

	@Test
	public void systemDeleteRecognizesUnauthorizedFileDel() throws InvalidProtocolBufferException {
		// given:
		var txn = exchangeRatesAdminTxn()
				.setSystemDelete(SystemDeleteTransactionBody
						.newBuilder()
						.setFileID(file(1234)));
		// expect:
		assertEquals(UNAUTHORIZED, subject.check(accessor(txn)));
	}

	@Test
	public void systemDeleteRecognizesAuthorizedFileDel() throws InvalidProtocolBufferException {
		// given:
		var txn = sysDeleteTxn()
				.setSystemDelete(SystemDeleteTransactionBody
						.newBuilder()
						.setFileID(file(1234)));
		// expect:
		assertEquals(AUTHORIZED, subject.check(accessor(txn)));
	}

	@Test
	public void systemDeleteRecognizesUnauthorizedContractDel() throws InvalidProtocolBufferException {
		// given:
		var txn = civilianTxn()
				.setSystemDelete(SystemDeleteTransactionBody
						.newBuilder()
						.setContractID(contract(1234)));
		// expect:
		assertEquals(UNAUTHORIZED, subject.check(accessor(txn)));
	}

	@Test
	public void systemDeleteRecognizesAuthorizedContractDel() throws InvalidProtocolBufferException {
		// given:
		var txn = sysDeleteTxn()
				.setSystemDelete(SystemDeleteTransactionBody
						.newBuilder()
						.setContractID(contract(1234)));
		// expect:
		assertEquals(AUTHORIZED, subject.check(accessor(txn)));
	}

	@Test
	public void fileAppendRecognizesUnnecessary() throws InvalidProtocolBufferException {
		// given:
		var txn = exchangeRatesAdminTxn()
				.setFileAppend(FileAppendTransactionBody
						.newBuilder()
						.setFileID(file(1122)));
		// expect:
		assertEquals(UNNECESSARY, subject.check(accessor(txn)));
	}

	@Test
	public void contractUpdateRecognizesAuthorized() throws InvalidProtocolBufferException {
		// given:
		var txn = treasuryTxn()
				.setContractUpdateInstance(ContractUpdateTransactionBody
						.newBuilder()
						.setContractID(contract(123)));
		// expect:
		assertEquals(AUTHORIZED, subject.check(accessor(txn)));
	}

	@Test
	public void contractUpdateRecognizesUnnecessary() throws InvalidProtocolBufferException {
		// given:
		var txn = treasuryTxn()
				.setContractUpdateInstance(ContractUpdateTransactionBody
						.newBuilder()
						.setContractID(contract(1233)));
		// expect:
		assertEquals(UNNECESSARY, subject.check(accessor(txn)));
	}

	@Test
	public void fileUpdateRecognizesAuthorized() throws InvalidProtocolBufferException {
		// given:
		var txn = exchangeRatesAdminTxn()
				.setFileUpdate(FileUpdateTransactionBody
						.newBuilder()
						.setFileID(file(112)));
		// expect:
		assertEquals(AUTHORIZED, subject.check(accessor(txn)));
	}

	@Test
	public void freezeAdminCanUpdateZipFile() throws InvalidProtocolBufferException {
		// given:
		var txn = freezeAdminTxn()
				.setFileUpdate(FileUpdateTransactionBody
						.newBuilder()
						.setFileID(file(150)));
		// expect:
		assertEquals(AUTHORIZED, subject.check(accessor(txn)));
	}


	@Test
	public void fileUpdateRecognizesUnnecessary() throws InvalidProtocolBufferException {
		// given:
		var txn = exchangeRatesAdminTxn()
				.setFileUpdate(FileUpdateTransactionBody
						.newBuilder()
						.setFileID(file(1122)));
		// expect:
		assertEquals(UNNECESSARY, subject.check(accessor(txn)));
	}

	@Test
	public void systemFilesCannotBeDeleted() throws InvalidProtocolBufferException {
		// given:
		var txn = treasuryTxn()
				.setFileDelete(FileDeleteTransactionBody
						.newBuilder()
						.setFileID(file(100)));

		// expect:
		assertEquals(IMPERMISSIBLE, subject.check(accessor(txn)));
	}

	@Test
	public void civilianFilesAreDeletable() throws InvalidProtocolBufferException {
		// given:
		var txn = treasuryTxn()
				.setFileDelete(FileDeleteTransactionBody
						.newBuilder()
						.setFileID(file(1001)));

		// expect:
		assertEquals(UNNECESSARY, subject.check(accessor(txn)));
	}

	@Test
	public void systemContractsCannotBeDeleted() throws InvalidProtocolBufferException {
		// given:
		var txn = treasuryTxn()
				.setContractDeleteInstance(ContractDeleteTransactionBody
						.newBuilder()
						.setContractID(contract(100)));

		// expect:
		assertEquals(IMPERMISSIBLE, subject.check(accessor(txn)));
	}

	@Test
	public void civilianContractsAreDeletable() throws InvalidProtocolBufferException {
		// given:
		var txn = treasuryTxn()
				.setContractDeleteInstance(ContractDeleteTransactionBody
						.newBuilder()
						.setContractID(contract(1001)));

		// expect:
		assertEquals(UNNECESSARY, subject.check(accessor(txn)));
	}

	@Test
	public void systemAccountsCannotBeDeleted() throws InvalidProtocolBufferException {
		// given:
		var txn = treasuryTxn()
				.setCryptoDelete(CryptoDeleteTransactionBody
						.newBuilder()
						.setDeleteAccountID(account(100)));

		// expect:
		assertEquals(IMPERMISSIBLE, subject.check(accessor(txn)));
	}

	@Test
	public void civilianAccountsAreDeletable() throws InvalidProtocolBufferException {
		// given:
		var txn = civilianTxn()
				.setCryptoDelete(CryptoDeleteTransactionBody
						.newBuilder()
						.setDeleteAccountID(account(1001)));

		// expect:
		assertEquals(UNNECESSARY, subject.check(accessor(txn)));
	}

	@Test
	public void createAccountAlwaysOk() throws InvalidProtocolBufferException {
		// given:
		var txn = civilianTxn()
				.setCryptoCreateAccount(CryptoCreateTransactionBody.getDefaultInstance());

		// expect:
		assertEquals(UNNECESSARY, subject.check(accessor(txn)));
	}

	private SignedTxnAccessor accessor(TransactionBody.Builder txn) throws InvalidProtocolBufferException {
		return new SignedTxnAccessor(Transaction.newBuilder().setBodyBytes(txn.build().toByteString()).build());
	}

	private TransactionBody.Builder civilianTxn() {
		return txnWithPayer(75231);
	}

	private TransactionBody.Builder treasuryTxn() {
		return txnWithPayer(2);
	}

	private TransactionBody.Builder freezeAdminTxn() {
		return txnWithPayer(58);
	}

	private TransactionBody.Builder sysAdminTxn() {
		return txnWithPayer(50);
	}

	private TransactionBody.Builder sysDeleteTxn() {
		return txnWithPayer(59);
	}

	private TransactionBody.Builder sysUndeleteTxn() {
		return txnWithPayer(60);
	}

	private TransactionBody.Builder exchangeRatesAdminTxn() {
		return txnWithPayer(57);
	}

	private TransactionBody.Builder txnWithPayer(long num) {
		return TransactionBody.newBuilder()
				.setTransactionID(TransactionID.newBuilder()
						.setAccountID(account(num)));

	}

	private ContractID contract(long num) {
		return IdUtils.asContract(String.format("0.0.%d", num));
	}

	private FileID file(long num) {
		return IdUtils.asFile(String.format("0.0.%d", num));
	}

	private AccountID account(long num) {
		return IdUtils.asAccount(String.format("0.0.%d", num));
	}
}
