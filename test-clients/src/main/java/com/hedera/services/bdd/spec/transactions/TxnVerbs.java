package com.hedera.services.bdd.spec.transactions;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.hedera.services.bdd.spec.transactions.consensus.HapiMessageSubmit;
import com.hedera.services.bdd.spec.transactions.consensus.HapiTopicCreate;
import com.hedera.services.bdd.spec.transactions.consensus.HapiTopicDelete;
import com.hedera.services.bdd.spec.transactions.consensus.HapiTopicUpdate;
import com.hedera.services.bdd.spec.transactions.network.HapiUncheckedSubmit;
import com.hedera.services.bdd.spec.transactions.schedule.HapiScheduleCreate;
import com.hedera.services.bdd.spec.transactions.schedule.HapiScheduleDelete;
import com.hedera.services.bdd.spec.transactions.schedule.HapiScheduleSign;
import com.hedera.services.bdd.spec.transactions.system.HapiSysDelete;
import com.hedera.services.bdd.spec.transactions.system.HapiSysUndelete;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenAssociate;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenBurn;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenDissociate;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenFreeze;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenCreate;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenDelete;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenKycGrant;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenKycRevoke;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenMint;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenUnfreeze;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenUpdate;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenWipe;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hederahashgraph.api.proto.java.ScheduleSign;
import com.hederahashgraph.api.proto.java.TopicID;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.transactions.contract.HapiContractCall;
import com.hedera.services.bdd.spec.transactions.contract.HapiContractCreate;
import com.hedera.services.bdd.spec.transactions.contract.HapiContractDelete;
import com.hedera.services.bdd.spec.transactions.contract.HapiContractUpdate;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoCreate;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoDelete;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoUpdate;
import com.hedera.services.bdd.spec.transactions.file.HapiFileAppend;
import com.hedera.services.bdd.spec.transactions.file.HapiFileCreate;
import com.hedera.services.bdd.spec.transactions.file.HapiFileDelete;
import com.hedera.services.bdd.spec.transactions.file.HapiFileUpdate;

import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import static com.hedera.services.bdd.suites.HapiApiSuite.salted;

public class TxnVerbs {
	/* CRYPTO */
	public static HapiCryptoCreate cryptoCreate(String account) {
		return new HapiCryptoCreate(account);
	}
	public static HapiCryptoDelete cryptoDelete(String account) {
		return new HapiCryptoDelete(account);
	}
	@SafeVarargs
	public static HapiCryptoTransfer cryptoTransfer(Function<HapiApiSpec, TransferList>... providers) {
		return new HapiCryptoTransfer(providers);
	}
	public static HapiCryptoTransfer cryptoTransfer(TokenMovement... sources) {
		return new HapiCryptoTransfer(sources);
	}
	public static HapiCryptoUpdate cryptoUpdate(String account) {
		return new HapiCryptoUpdate(account);
	}

	/* CONSENSUS */
	public static HapiTopicCreate createTopic(String topic) {
		return new HapiTopicCreate(topic);
	}
	public static HapiTopicDelete deleteTopic(String topic) {
		return new HapiTopicDelete(topic);
	}
	public static HapiTopicDelete deleteTopic(Function<HapiApiSpec, TopicID> topicFn) {
		return new HapiTopicDelete(topicFn);
	}
	public static HapiTopicUpdate updateTopic(String topic) {
		return new HapiTopicUpdate(topic);
	}
	public static HapiMessageSubmit submitMessageTo(String topic) {
		return new HapiMessageSubmit(topic);
	}
	public static HapiMessageSubmit submitMessageTo(Function<HapiApiSpec, TopicID> topicFn) {
		return new HapiMessageSubmit(topicFn);
	}

	/* FILE */
	public static HapiFileCreate fileCreate(String fileName) {
		return new HapiFileCreate(fileName);
	}
	public static HapiFileAppend fileAppend(String fileName) {
		return new HapiFileAppend(fileName);
	}
	public static HapiFileUpdate fileUpdate(String fileName) {
		return new HapiFileUpdate(fileName);
	}
	public static HapiFileDelete fileDelete(String fileName) {
		return new HapiFileDelete(fileName);
	}
	public static HapiFileDelete fileDelete(Supplier<String> fileNameSupplier) {
		return new HapiFileDelete(fileNameSupplier);
	}

	/* TOKEN */
	public static HapiTokenDissociate tokenDissociate(String account, String... tokens) {
		return new HapiTokenDissociate(account, tokens);
	}
	public static HapiTokenAssociate tokenAssociate(String account, String... tokens) {
		return new HapiTokenAssociate(account, tokens);
	}
	public static HapiTokenAssociate tokenAssociate(String account, List<String> tokens) {
		return new HapiTokenAssociate(account, tokens);
	}
	public static HapiTokenCreate tokenCreate(String token) {
		return new HapiTokenCreate(token).name(salted(token));
	}
	public static HapiTokenUpdate tokenUpdate(String token) {
		return new HapiTokenUpdate(token);
	}
	public static HapiTokenDelete tokenDelete(String token) {
		return new HapiTokenDelete(token);
	}
	public static HapiTokenFreeze tokenFreeze(String token, String account) {
		return new HapiTokenFreeze(token, account);
	}
	public static HapiTokenUnfreeze tokenUnfreeze(String token, String account) {
		return new HapiTokenUnfreeze(token, account);
	}
	public static HapiTokenKycGrant grantTokenKyc(String token, String account) {
		return new HapiTokenKycGrant(token, account);
	}
	public static HapiTokenKycRevoke revokeTokenKyc(String token, String account) {
		return new HapiTokenKycRevoke(token, account);
	}
	public static HapiTokenWipe wipeTokenAccount(String token, String account, long amount) {
		return new HapiTokenWipe(token, account, amount);
	}
	public static HapiTokenMint mintToken(String token, long amount) {
		return new HapiTokenMint(token, amount);
	}
	public static HapiTokenBurn burnToken(String token, long amount) {
		return new HapiTokenBurn(token, amount);
	}

	/* SCHEDULE */
	public static <T extends HapiTxnOp<T>> HapiScheduleCreate<T> scheduleCreate(String scheduled, HapiTxnOp<T> txn) {
		return new HapiScheduleCreate<>(scheduled, txn);
	}

	public static HapiScheduleSign scheduleSign(String schedule) {
		return new HapiScheduleSign(schedule);
	}

	public static HapiScheduleCreate<HapiCryptoCreate> scheduleCreateNonsense(String scheduled) {
		return new HapiScheduleCreate<>(scheduled, cryptoCreate("doomed")).garbled();
	}

	public static HapiScheduleCreate<HapiCryptoCreate> scheduleCreateFunctionless(String scheduled) {
		return new HapiScheduleCreate<>(scheduled, cryptoCreate("doomed")).functionless();
	}

	public static HapiScheduleDelete scheduleDelete(String schedule) {
		return new HapiScheduleDelete(schedule);
	}

	/* SYSTEM */
	public static HapiSysDelete systemFileDelete(String target) {
		return new HapiSysDelete().file(target);
	}
	public static HapiSysUndelete systemFileUndelete(String target) {
		return new HapiSysUndelete().file(target);
	}


	public static HapiSysDelete systemContractDelete(String target) {
		return new HapiSysDelete().contract(target);
	}
	public static HapiSysUndelete systemContractUndelete(String target) {
		return new HapiSysUndelete().contract(target);
	}

	/* NETWORK */
	public static <T extends HapiTxnOp<T>> HapiUncheckedSubmit<T> uncheckedSubmit(HapiTxnOp<T> subOp) {
		return new HapiUncheckedSubmit<>(subOp);
	}

	/* SMART CONTRACT */
	public static HapiContractCall contractCallFrom(String details) {
		return HapiContractCall.fromDetails(details);
	}
	public static HapiContractCall contractCall(String contract) {
		return new HapiContractCall(contract);
	}
	public static HapiContractCall contractCall(String contract, String abi, Object... params) {
		return new HapiContractCall(abi, contract, params);
	}
	public static HapiContractCall contractCall(String contract, String abi, Function<HapiApiSpec, Object[]> fn) {
		return new HapiContractCall(abi, contract, fn);
	}
	public static HapiContractCreate contractCreate(String contract) {
		return new HapiContractCreate(contract);
	}
	public static HapiContractCreate contractCreate(String contract, String abi, Object... params) {
		return new HapiContractCreate(contract, abi, params);
	}
	public static HapiContractDelete contractDelete(String contract) {
		return new HapiContractDelete(contract);
	}
	public static HapiContractUpdate contractUpdate(String contract) {
		return new HapiContractUpdate(contract);
	}
}
