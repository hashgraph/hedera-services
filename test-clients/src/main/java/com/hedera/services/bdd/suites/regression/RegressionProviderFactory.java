package com.hedera.services.bdd.suites.regression;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.bdd.spec.infrastructure.EntityNameProvider;
import com.hedera.services.bdd.spec.infrastructure.OpProvider;
import com.hedera.services.bdd.spec.infrastructure.meta.ActionableContractCall;
import com.hedera.services.bdd.spec.infrastructure.meta.ActionableContractCallLocal;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.BiasedDelegatingProvider;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.consensus.RandomMessageSubmit;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.consensus.RandomTopicCreation;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.consensus.RandomTopicDeletion;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.consensus.RandomTopicInfo;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.consensus.RandomTopicUpdate;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.contract.RandomCall;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.contract.RandomCallLocal;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.contract.RandomContract;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.contract.RandomContractDeletion;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.crypto.RandomAccount;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.crypto.RandomAccountDeletion;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.crypto.RandomAccountInfo;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.crypto.RandomAccountRecords;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.crypto.RandomAccountUpdate;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.crypto.RandomTransfer;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.files.RandomAppend;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.files.RandomContents;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.files.RandomFile;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.files.RandomFileDeletion;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.files.RandomFileInfo;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.files.RandomFileUpdate;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.inventory.KeyInventoryCreation;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.meta.RandomReceipt;
import com.hedera.services.bdd.spec.infrastructure.providers.ops.meta.RandomRecord;
import com.hedera.services.bdd.spec.infrastructure.selectors.RandomSelector;
import com.hedera.services.bdd.spec.props.JutilPropertySource;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.TopicID;

import java.util.function.Function;
import java.util.function.Supplier;

public class RegressionProviderFactory {
	public static final String RESOURCE_DIR = "eet-config";

	public static Function<HapiApiSpec, OpProvider> factoryFrom(Supplier<String> resource) {
		return spec -> {
			String path = RESOURCE_DIR + "/" + resource.get();
			HapiPropertySource props = new JutilPropertySource(path);

			EntityNameProvider<Key> keys = new RegistrySourcedNameProvider<>(
				Key.class,
				spec.registry(),
				new RandomSelector());
			RegistrySourcedNameProvider<FileID> files = new RegistrySourcedNameProvider<>(
					FileID.class,
					spec.registry(),
					new RandomSelector());
			RegistrySourcedNameProvider<AccountID> accounts = new RegistrySourcedNameProvider<>(
					AccountID.class,
					spec.registry(),
					new RandomSelector());
			RegistrySourcedNameProvider<ContractID> contracts = new RegistrySourcedNameProvider<>(
					ContractID.class,
					spec.registry(),
					new RandomSelector());
			RegistrySourcedNameProvider<ActionableContractCall> calls = new RegistrySourcedNameProvider<>(
					ActionableContractCall.class,
					spec.registry(),
					new RandomSelector());
			RegistrySourcedNameProvider<ActionableContractCallLocal> localCalls = new RegistrySourcedNameProvider<>(
					ActionableContractCallLocal.class,
					spec.registry(),
					new RandomSelector());

			RegistrySourcedNameProvider<TopicID> topics = new RegistrySourcedNameProvider<>(
					TopicID.class,
					spec.registry(),
					new RandomSelector());

			KeyInventoryCreation keyInventory = new KeyInventoryCreation();

			return new BiasedDelegatingProvider()
					/* --- <inventory> --- */
					.withInitialization(
							keyInventory.creationOps())
					/* ----- META ----- */
					.withOp(
							new RandomRecord(spec.txns()),
							props.getInteger("randomRecord.bias"))
					.withOp(
							new RandomReceipt(spec.txns()),
							props.getInteger("randomReceipt.bias"))
					/* ----- CRYPTO ----- */
					.withOp(
							new RandomAccount(keys, accounts)
									.ceiling(intPropOrElse(
											"randomAccount.ceilingNum",
											RandomAccount.DEFAULT_CEILING_NUM,
											props)),
							props.getInteger("randomAccount.bias"))
					.withOp(
							new RandomTransfer(accounts)
									.recordProbability(
											doublePropOrElse(
													"randomTransfer.recordProbability",
													RandomTransfer.DEFAULT_RECORD_PROBABILITY,
													props)),
							props.getInteger("randomTransfer.bias"))
					.withOp(
							new RandomAccountUpdate(keys, accounts),
							props.getInteger("randomAccountUpdate.bias"))
					.withOp(
							new RandomAccountDeletion(accounts),
							props.getInteger("randomAccountDeletion.bias"))
					.withOp(
							new RandomAccountInfo(accounts),
							props.getInteger("randomAccountInfo.bias"))
					.withOp(
							new RandomAccountRecords(accounts),
							props.getInteger("randomAccountRecords.bias"))
					/* ---- CONSENSUS ---- */
					.withOp(
							new RandomTopicCreation(keys, topics)
									.ceiling(intPropOrElse(
											"randomTopicCreation.ceilingNum",
											RandomFile.DEFAULT_CEILING_NUM,
											props)),
							intPropOrElse("randomTopicCreation.bias", 0, props))
					.withOp(
							new RandomTopicDeletion(topics),
							intPropOrElse("randomTopicDeletion.bias", 0, props))
					.withOp(
							new RandomTopicUpdate(topics),
							intPropOrElse("randomTopicUpdate.bias", 0, props))
					.withOp(
							new RandomMessageSubmit(topics),
							intPropOrElse("randomMessageSubmit.bias", 0, props))
					.withOp(
							new RandomTopicInfo(topics),
							intPropOrElse("randomTopicInfo.bias", 0, props))

					/* ---- FILE ---- */
					.withOp(
							new RandomFile(files)
									.ceiling(intPropOrElse(
											"randomFile.ceilingNum",
											RandomFile.DEFAULT_CEILING_NUM,
											props)),
							intPropOrElse("randomFile.bias", 0, props))
					.withOp(
							new RandomFileDeletion(files),
							intPropOrElse("randomFileDeletion.bias", 0, props))
					.withOp(
							new RandomFileUpdate(files),
							intPropOrElse("randomFileUpdate.bias", 0, props))
					.withOp(
							new RandomAppend(files),
							intPropOrElse("randomAppend.bias", 0, props))
					.withOp(
							new RandomFileInfo(files),
							intPropOrElse("randomFileInfo.bias", 0, props))
					.withOp(
							new RandomContents(files),
							intPropOrElse("randomContents.bias", 0, props))
					/* ---- CONTRACT ---- */
					.withOp(
							new RandomCall(calls),
							props.getInteger("randomCall.bias"))
					.withOp(
							new RandomCallLocal(localCalls),
							props.getInteger("randomCallLocal.bias"))
					.withOp(
							new RandomContractDeletion(accounts, contracts),
							props.getInteger("randomContractDeletion.bias"))
					.withOp(
							new RandomContract(keys, contracts)
									.ceiling(intPropOrElse(
											"randomContract.ceilingNum",
											RandomContract.DEFAULT_CEILING_NUM,
											props)),
							props.getInteger("randomContract.bias"));
		};
	}

	private static double doublePropOrElse(String name, double defaultValue, HapiPropertySource props) {
		return props.has(name) ? props.getDouble(name) : defaultValue;
	}

	private static int intPropOrElse(String name, int defaultValue, HapiPropertySource props) {
		return props.has(name) ? props.getInteger(name) : defaultValue;
	}
}
