package com.hedera.test.mocks;

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

import com.google.common.cache.CacheBuilder;
import com.hedera.services.config.MockGlobalDynamicProps;
import com.hedera.services.context.properties.BootstrapProperties;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.context.properties.StandardizedPropertySources;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
import com.hedera.services.fees.calculation.TxnResourceUsageEstimator;
import com.hedera.services.fees.calculation.UsageBasedFeeCalculator;
import com.hedera.services.fees.calculation.consensus.queries.GetTopicInfoResourceUsage;
import com.hedera.services.fees.calculation.consensus.txns.CreateTopicResourceUsage;
import com.hedera.services.fees.calculation.consensus.txns.DeleteTopicResourceUsage;
import com.hedera.services.fees.calculation.consensus.txns.SubmitMessageResourceUsage;
import com.hedera.services.fees.calculation.consensus.txns.UpdateTopicResourceUsage;
import com.hedera.services.fees.calculation.contract.txns.ContractCallResourceUsage;
import com.hedera.services.fees.calculation.contract.txns.ContractCreateResourceUsage;
import com.hedera.services.fees.calculation.contract.txns.ContractDeleteResourceUsage;
import com.hedera.services.fees.calculation.contract.txns.ContractUpdateResourceUsage;
import com.hedera.services.fees.calculation.crypto.queries.GetAccountInfoResourceUsage;
import com.hedera.services.fees.calculation.crypto.queries.GetAccountRecordsResourceUsage;
import com.hedera.services.fees.calculation.crypto.queries.GetTxnRecordResourceUsage;
import com.hedera.services.fees.calculation.crypto.txns.CryptoCreateResourceUsage;
import com.hedera.services.fees.calculation.crypto.txns.CryptoDeleteResourceUsage;
import com.hedera.services.fees.calculation.crypto.txns.CryptoTransferResourceUsage;
import com.hedera.services.fees.calculation.crypto.txns.CryptoUpdateResourceUsage;
import com.hedera.services.fees.calculation.file.txns.FileAppendResourceUsage;
import com.hedera.services.fees.calculation.file.txns.FileCreateResourceUsage;
import com.hedera.services.fees.calculation.file.txns.FileDeleteResourceUsage;
import com.hedera.services.fees.calculation.file.txns.FileUpdateResourceUsage;
import com.hedera.services.fees.calculation.file.txns.SystemDeleteFileResourceUsage;
import com.hedera.services.fees.calculation.file.txns.SystemUndeleteFileResourceUsage;
import com.hedera.services.fees.calculation.system.txns.FreezeResourceUsage;
import com.hedera.services.queries.answering.AnswerFunctions;
import com.hedera.services.records.RecordCache;
import com.hedera.services.usage.crypto.CryptoOpsUsage;
import com.hedera.services.usage.file.FileOpsUsage;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.fee.CryptoFeeBuilder;
import com.hederahashgraph.fee.FileFeeBuilder;
import com.hederahashgraph.fee.SmartContractFeeBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static com.hedera.test.mocks.TestExchangeRates.TEST_EXCHANGE;
import static com.hedera.test.mocks.TestUsagePricesProvider.TEST_USAGE_PRICES;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusCreateTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusDeleteTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusUpdateTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCall;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileAppend;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.Freeze;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.SystemDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.SystemUndelete;
import static java.util.Map.entry;

public enum TestFeesFactory {
	FEES_FACTORY;

	public FeeCalculator get() {
		return getWithExchange(TEST_EXCHANGE);
	}

	public FeeCalculator getWithExchange(HbarCentExchange exchange) {
		CryptoOpsUsage cryptoOpsUsage = new CryptoOpsUsage();
		FileOpsUsage fileOpsUsage = new FileOpsUsage();
		FileFeeBuilder fileFees = new FileFeeBuilder();
		CryptoFeeBuilder cryptoFees = new CryptoFeeBuilder();
		SmartContractFeeBuilder contractFees = new SmartContractFeeBuilder();
		PropertySource properties =
				new StandardizedPropertySources(new BootstrapProperties(), ignore -> true).asResolvingSource();
		AnswerFunctions answerFunctions = new AnswerFunctions();
		RecordCache recordCache = new RecordCache(
				null,
				CacheBuilder.newBuilder().build(),
				new HashMap<>());

		return new UsageBasedFeeCalculator(
				exchange,
				TEST_USAGE_PRICES,
				List.of(
						/* Meta */
						new GetTxnRecordResourceUsage(recordCache, answerFunctions, cryptoFees),
						/* Crypto */
						new GetAccountInfoResourceUsage(cryptoOpsUsage),
						new GetAccountRecordsResourceUsage(answerFunctions, cryptoFees),
						/* Consensus */
						new GetTopicInfoResourceUsage()
				),
				txnUsageFn(cryptoOpsUsage, fileOpsUsage, fileFees, cryptoFees, contractFees)
		);
	}

	private Function<HederaFunctionality, List<TxnResourceUsageEstimator>> txnUsageFn(
			CryptoOpsUsage cryptoOpsUsage,
			FileOpsUsage fileOpsUsage,
			FileFeeBuilder fileFees,
			CryptoFeeBuilder cryptoFees,
			SmartContractFeeBuilder contractFees
	) {
		return Map.ofEntries(
				/* Crypto */
				entry(CryptoCreate, List.<TxnResourceUsageEstimator>of(new CryptoCreateResourceUsage(cryptoOpsUsage))),
				entry(CryptoDelete, List.<TxnResourceUsageEstimator>of(new CryptoDeleteResourceUsage(cryptoFees))),
				entry(CryptoUpdate, List.<TxnResourceUsageEstimator>of(new CryptoUpdateResourceUsage(cryptoOpsUsage))),
				entry(CryptoTransfer, List.<TxnResourceUsageEstimator>of(new CryptoTransferResourceUsage(new MockGlobalDynamicProps()))),
				/* Contract */
				entry(ContractCall, List.<TxnResourceUsageEstimator>of(new ContractCallResourceUsage(contractFees))),
				entry(ContractCreate, List.<TxnResourceUsageEstimator>of(new ContractCreateResourceUsage(contractFees))),
				entry(ContractDelete, List.<TxnResourceUsageEstimator>of(new ContractDeleteResourceUsage(contractFees))),
				entry(ContractUpdate, List.<TxnResourceUsageEstimator>of(new ContractUpdateResourceUsage(contractFees))),
				/* File */
				entry(FileCreate, List.<TxnResourceUsageEstimator>of(new FileCreateResourceUsage(fileOpsUsage))),
				entry(FileDelete, List.<TxnResourceUsageEstimator>of(new FileDeleteResourceUsage(fileFees))),
				entry(FileUpdate, List.<TxnResourceUsageEstimator>of(new FileUpdateResourceUsage(fileOpsUsage))),
				entry(FileAppend, List.<TxnResourceUsageEstimator>of(new FileAppendResourceUsage(fileFees))),
				/* Consensus */
				entry(ConsensusCreateTopic, List.<TxnResourceUsageEstimator>of(new CreateTopicResourceUsage())),
				entry(ConsensusUpdateTopic, List.<TxnResourceUsageEstimator>of(new UpdateTopicResourceUsage())),
				entry(ConsensusDeleteTopic, List.<TxnResourceUsageEstimator>of(new DeleteTopicResourceUsage())),
				entry(ConsensusSubmitMessage, List.<TxnResourceUsageEstimator>of(new SubmitMessageResourceUsage())),
				/* System */
				entry(Freeze, List.<TxnResourceUsageEstimator>of(new FreezeResourceUsage())),
				entry(SystemDelete, List.<TxnResourceUsageEstimator>of(new SystemDeleteFileResourceUsage(fileFees))),
				entry(SystemUndelete, List.<TxnResourceUsageEstimator>of(new SystemUndeleteFileResourceUsage(fileFees)))
		)::get;
	}
}
