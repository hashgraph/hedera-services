package com.hedera.services.bdd.spec.utilops;

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

import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.keys.KeyShape;
import com.hedera.services.bdd.spec.transactions.contract.HapiContractCall;
import com.hedera.services.bdd.spec.transactions.contract.HapiEthereumCall;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hedera.services.bdd.spec.utilops.inventory.NewSpecKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.hedera.services.bdd.suites.HapiApiSuite.ETH_SUFFIX;
import static com.hedera.services.bdd.suites.HapiApiSuite.GENESIS;
import static com.hedera.services.bdd.suites.HapiApiSuite.ONE_HUNDRED_HBARS;

public class CustomSpecAssert extends UtilOp {
	static final Logger log = LogManager.getLogger(CustomSpecAssert.class);
	private static final String secp256k1SourceKey = "secp256k1Alias";
	private static final KeyShape secp256k1Shape = KeyShape.SECP256K1;
	private static final Map<String, Boolean> specToInitializedEthereumContract = new HashMap<>();

	public static void allRunFor(HapiApiSpec spec, List<HapiSpecOperation> ops) {
		if(spec.getSuitePrefix().endsWith(ETH_SUFFIX)) {
			if(!specToInitializedEthereumContract.containsKey(spec.getName())) {
				initializeEthereumAccountForSpec(spec);
				specToInitializedEthereumContract.putIfAbsent(spec.getName(), true);
			}

			executeEthereumOps(spec, ops);
		} else {
			executeHederaOps(spec, ops);
		}
	}

	private static void executeHederaOps(HapiApiSpec spec, List<HapiSpecOperation> ops) {
		for (HapiSpecOperation op : ops) {
			Optional<Throwable>	error = op.execFor(spec);
			if (error.isPresent()) {
				log.error("Operation '" + op.toString() + "' :: " + error.get().getMessage());
				throw new IllegalStateException(error.get());
			}
		}
	}

	private static void executeEthereumOps(HapiApiSpec spec, List<HapiSpecOperation> ops) {
		for (HapiSpecOperation op : ops) {
			if (op instanceof HapiContractCall) {
				op = new HapiEthereumCall(((HapiContractCall) op));
				((HapiEthereumCall) op).signingWith(secp256k1SourceKey)
						.type(EthTxData.EthTransactionType.LEGACY_ETHEREUM)
						.gas(500_000L)
						.gasPrice(10L)
						.maxGasAllowance(5L)
						.maxPriorityGas(2L)
						.gasLimit(1_000_000L);
			}

			Optional<Throwable>	error = op.execFor(spec);
			if (error.isPresent()) {
				log.error("Operation '" + op.toString() + "' :: " + error.get().getMessage());
				throw new IllegalStateException(error.get());
			}
		}
	}

	private static void initializeEthereumAccountForSpec(HapiApiSpec spec) {
		final var newSpecKey = new NewSpecKey(secp256k1SourceKey).shape(secp256k1Shape);
		final var cryptoTransfer = new HapiCryptoTransfer(HapiCryptoTransfer.tinyBarsFromAccountToAlias(GENESIS, secp256k1SourceKey, ONE_HUNDRED_HBARS));

		newSpecKey.execFor(spec);
		cryptoTransfer.execFor(spec);
	}

	public static void allRunFor(HapiApiSpec spec, HapiSpecOperation... ops) {
		allRunFor(spec, List.of(ops));
	}

	@FunctionalInterface
	public interface ThrowingConsumer {
		void assertFor(HapiApiSpec spec, Logger assertLog) throws Throwable;
	}

	private final ThrowingConsumer custom;

	public CustomSpecAssert(ThrowingConsumer custom) {
		this.custom = custom;
	}

	@Override
	protected boolean submitOp(HapiApiSpec spec) throws Throwable {
		custom.assertFor(spec, log);
		return false;
	}

	@Override
	public String toString() {
		return "CustomSpecAssert";
	}
}
