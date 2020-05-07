package com.hedera.services.bdd.spec.utilops.throughput;

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

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.queries.HapiQueryOp;
import com.hedera.services.bdd.spec.stats.ThroughputObs;
import com.hedera.services.bdd.spec.utilops.CustomSpecAssert;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import java.util.Optional;
import java.util.function.Supplier;

public class FinishThroughputObs extends UtilOp {
	static final Logger log = LogManager.getLogger(FinishThroughputObs.class);

	final private String name;
	Optional<Long> sleepMs = Optional.empty();
	Optional<Long> maxObsLengthMs = Optional.empty();
	Optional<Supplier<HapiQueryOp<?>[]>> gateSupplier = Optional.empty();

	public FinishThroughputObs(String name) {
		this.name = name;
	}

	public FinishThroughputObs gatedByQueries(Supplier<HapiQueryOp<?>[]> queriesSupplier) {
		gateSupplier = Optional.of(queriesSupplier);
		return this;
	}
	public FinishThroughputObs gatedByQuery(Supplier<HapiQueryOp<?>> querySupplier) {
		gateSupplier = Optional.of(() -> new HapiQueryOp<?>[] { querySupplier.get() });
		return this;
	}
	public FinishThroughputObs sleepMs(long length) {
		sleepMs = Optional.of(length);
		return this;
	}
	public FinishThroughputObs expiryMs(long length) {
		maxObsLengthMs = Optional.of(length);
		return this;
	}

	@Override
	protected boolean submitOp(HapiApiSpec spec) throws Throwable {
		ThroughputObs baseObs = spec.registry().getThroughputObs(name);

		if (!gateSupplier.isPresent()) {
			int n = spec.numLedgerOps() - baseObs.getNumOpsAtObservationStart();
			long timeToOpenGate = System.currentTimeMillis() - baseObs.getCreationTime();
			log.info(spec.logPrefix() + this + " saw " + n + " ops for its throughput measurement.");
			baseObs.setNumOpsAtObservationFinish(spec.numLedgerOps());
			baseObs.setObsLengthMs(timeToOpenGate);
			return false;
		}

		sleepUntil(baseObs.getExpectedQueueSaturationTime());
		if (baseObs.getNumOpsAtExpectedQueueSaturation() == -1) {
			log.warn(spec.logPrefix() + this + " saw no ops executed after the expected queue saturation time!");
			return false;
		}
		log.info(spec.logPrefix() + this + " "
				+ baseObs.getNumOpsAtExpectedQueueSaturation() + " ops had been executed at queue saturation time.");

		long now = System.currentTimeMillis();
		long sleepTimeMs = sleepMs.orElse(spec.setup().defaultThroughputObsSleepMs());
		long obsExpirationTime = now + maxObsLengthMs.orElse(spec.setup().defaultThroughputObsExpiryMs());
		while (now < obsExpirationTime) {
			HapiQueryOp<?>[] gatingQueries = gateSupplier.get().get();
			if (gatingQueries.length == 0) {
				break;
			}
			try {
				CustomSpecAssert.allRunFor(spec, gatingQueries);
				break;
			} catch (Throwable ignore) { }
			log.info(spec.logPrefix() + this + " sleeping " + sleepTimeMs + "ms before retrying gate!");
			try {
				Thread.sleep(sleepTimeMs);
			} catch (InterruptedException ignore) {}
			now = System.currentTimeMillis();
		}

		if (now < obsExpirationTime) {
			int n = spec.numLedgerOps() - baseObs.getNumOpsAtExpectedQueueSaturation();
			long timeToOpenGate = now - baseObs.getExpectedQueueSaturationTime();
			log.info(spec.logPrefix() + this
					+ " observed " + n + " ops before gating queries passed in " + timeToOpenGate + "ms.");
			baseObs.setNumOpsAtObservationFinish(spec.numLedgerOps());
			baseObs.setObsLengthMs(timeToOpenGate);
		} else {
			log.warn(spec.logPrefix() + this + " never saw its gating queries pass!");
		}

		return false;
	}

	private void sleepUntil(long t) {
		long now = System.currentTimeMillis();
		while (now < t) {
			try {
				Thread.sleep(t - now + 1L);
			} catch (InterruptedException ignore) {}
			now = System.currentTimeMillis();
		}
	}

	@Override
	public String toString() {
		return MoreObjects.toStringHelper(this).add("finishing", name).toString();
	}
}
