package com.hedera.services.bdd.spec.fees;

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

import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.CurrentAndNextFeeSchedule;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FeeSchedule;
import com.hederahashgraph.api.proto.java.FileGetContentsQuery;
import com.hederahashgraph.api.proto.java.FileGetContentsResponse;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransferList;
import com.hedera.services.bdd.spec.infrastructure.HapiApiClients;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import com.hedera.services.bdd.spec.HapiSpecSetup;
import com.hedera.services.bdd.spec.keys.KeyFactory;
import com.hedera.services.bdd.spec.transactions.TxnFactory;
import org.junit.Assert;

import static com.hedera.services.bdd.spec.queries.QueryUtils.*;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.asTransferList;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.tinyBarsFromTo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.nio.file.Files;
import java.time.Instant;
import java.util.Arrays;

public class FeesAndRatesProvider {
	private static final Logger log = LogManager.getLogger(FeesAndRatesProvider.class);

	private TxnFactory txns;
	private KeyFactory keys;
	private HapiSpecSetup setup;
	private HapiApiClients clients;
	private HapiSpecRegistry registry;
	private Instant lastRatesUpdate;
	static private FeeSchedule feeSchedule;
	static private ExchangeRateSet rateSet;
	static private ExchangeRateSet midnightRateSet;

	public FeesAndRatesProvider(TxnFactory txns, KeyFactory keys, HapiSpecSetup setup,
			HapiApiClients clients, HapiSpecRegistry registry) {
		this.txns = txns;
		this.keys = keys;
		this.setup = setup;
		this.clients = clients;
		this.registry = registry;
	}

	public void init() throws Throwable {
		if (setup.clientExchangeRatesFromDisk()) {
			readRateSet();
		} else {
			downloadRateSet();
		}
		if (setup.clientFeeScheduleFromDisk()) {
			readFeeSchedule();
		} else {
			downloadFeeSchedule();
		}
	}

	public FeeSchedule currentSchedule() {
		return feeSchedule;
	}

	public ExchangeRate rates() {
		return activeRates();
	}

	public void updateRateSet(ExchangeRateSet newSet) {
		rateSet = newSet;
		log.info("Updating rates! Now :: " + rateSetAsString(newSet));
	}

	public ExchangeRateSet rateSet() {
		return rateSet;
	}

	private ExchangeRate activeRates() {
		boolean useCurrent = Instant.now().getEpochSecond() < rateSet.getCurrentRate().getExpirationTime().getSeconds();

		return useCurrent ? rateSet.getCurrentRate() : rateSet.getNextRate();
	}

	private void readRateSet() throws Throwable {
		File f = new File(setup.clientExchangeRatesPath());
		byte[] bytes = Files.readAllBytes(f.toPath());
		rateSet = ExchangeRateSet.parseFrom(bytes);
		lastRatesUpdate = Instant.now();
		log.info("The exchange rates from '" + f.getAbsolutePath() + "' are :: " + rateSetAsString(rateSet));
	}

	private void downloadRateSet() throws Throwable {
		long queryFee = lookupDownloadFee(setup.exchangeRatesId());
		Transaction payment = genesisSponsored(queryFee);
		FileGetContentsResponse response = downloadWith(payment, false, setup.exchangeRatesId());
		byte[] bytes = response.getFileContents().getContents().toByteArray();
		rateSet = ExchangeRateSet.parseFrom(bytes);
		lastRatesUpdate = Instant.now();
		log.info("The exchange rates are :: " + rateSetAsString(rateSet));
	}

	private void readFeeSchedule() throws Throwable {
		File f = new File(setup.clientFeeSchedulePath());
		byte[] bytes = Files.readAllBytes(f.toPath());
		CurrentAndNextFeeSchedule wrapper = CurrentAndNextFeeSchedule.parseFrom(bytes);
		feeSchedule = wrapper.getCurrentFeeSchedule();
		log.info("The fee schedule from '" + f.getAbsolutePath()
				+ "' covers " + feeSchedule.getTransactionFeeScheduleList().size() + " ops.");
	}

	private void downloadFeeSchedule() throws Throwable {
		long queryFee = lookupDownloadFee(setup.feeScheduleId());
		Transaction payment = genesisSponsored(queryFee);
		FileGetContentsResponse response = downloadWith(payment, false, setup.feeScheduleId());
		byte[] bytes = response.getFileContents().getContents().toByteArray();
		CurrentAndNextFeeSchedule wrapper = CurrentAndNextFeeSchedule.parseFrom(bytes);
		feeSchedule = wrapper.getCurrentFeeSchedule();
		log.info("The fee schedule covers " + feeSchedule.getTransactionFeeScheduleList().size() + " ops.");
	}

	private long lookupDownloadFee(FileID fileId) throws Throwable {
		Transaction txn = genesisSponsored(setup.feeScheduleFetchFee());
		return downloadWith(txn, true, fileId).getHeader().getCost();
	}

	private FileGetContentsResponse downloadWith(Transaction payment, boolean costOnly, FileID fileId) {
		Query query = downloadQueryWith(payment, costOnly, fileId);
		FileGetContentsResponse response = clients
				.getFileSvcStub(setup.defaultNode(), setup.getConfigTLS())
				.getFileContent(query)
				.getFileGetContents();
		Assert.assertEquals(OK, response.getHeader().getNodeTransactionPrecheckCode());
		return response;
	}

	private Query downloadQueryWith(Transaction payment, boolean costOnly, FileID fileId) {
		FileGetContentsQuery costQuery = FileGetContentsQuery.newBuilder()
				.setHeader(costOnly ? answerCostHeader(payment) : answerHeader(payment))
				.setFileID(fileId)
				.build();
		return Query.newBuilder().setFileGetContents(costQuery).build();
	}

	private Transaction genesisSponsored(long queryFee) throws Throwable {
		TransferList transfers = asTransferList(
				tinyBarsFromTo(queryFee, setup.defaultPayer(), setup.defaultNode()));
		CryptoTransferTransactionBody opBody = txns
				.<CryptoTransferTransactionBody, CryptoTransferTransactionBody.Builder>
						body(CryptoTransferTransactionBody.class, b -> b.setTransfers(transfers));
		Transaction.Builder txnBuilder = txns.getReadyToSign(b -> b.setCryptoTransfer(opBody));
		return keys.getSigned(txnBuilder, Arrays.asList(registry.getKey(setup.defaultPayerName())));
	}

	public ExchangeRateSet rateSetWith(int curHbarEquiv, int curCentEquiv) {
		ExchangeRate.Builder curRate = rateSet
				.getCurrentRate().toBuilder()
				.setHbarEquiv(curHbarEquiv).setCentEquiv(curCentEquiv);

		ExchangeRateSet perturbedSet = rateSet.toBuilder().setCurrentRate(curRate).build();

		log.info("Computed a new rate set :: " + rateSetAsString(perturbedSet));

		return perturbedSet;
	}

	public ExchangeRateSet rateSetWith(int curHbarEquiv, int curCentEquiv, int nextHbarEquiv, int nextCentEquiv) {
		ExchangeRate.Builder curRate = rateSet
				.getCurrentRate().toBuilder()
				.setHbarEquiv(curHbarEquiv).setCentEquiv(curCentEquiv);

		ExchangeRate.Builder nextRate = rateSet
				.getCurrentRate().toBuilder()
				.setHbarEquiv(nextHbarEquiv).setCentEquiv(nextCentEquiv);

		ExchangeRateSet perturbedSet = rateSet.toBuilder().setCurrentRate(curRate).setNextRate(nextRate).build();

		log.info("Computed a new rate set :: " + rateSetAsString(perturbedSet));

		return perturbedSet;
	}

	public static String rateSetAsString(ExchangeRateSet set) {
		return "[Current hbar/cent equiv "
				+ set.getCurrentRate().getHbarEquiv() + " <-> "
				+ set.getCurrentRate().getCentEquiv() + " expiry @ "
				+ set.getCurrentRate().getExpirationTime().getSeconds() + ", "
				+ "Next hbar/cent equiv "
				+ set.getNextRate().getHbarEquiv() + " <-> "
				+ set.getNextRate().getCentEquiv() + " expiry @ "
				+ set.getNextRate().getExpirationTime().getSeconds() + "]";
	}
}
