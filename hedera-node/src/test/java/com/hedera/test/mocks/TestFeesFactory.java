package com.hedera.test.mocks;

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

import com.google.common.cache.CacheBuilder;
import com.hedera.services.context.properties.BootstrapProperties;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.context.properties.PropertySources;
import com.hedera.services.context.properties.StandardizedPropertySources;
import com.hedera.services.fees.FeeCalculator;
import com.hedera.services.fees.HbarCentExchange;
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
import com.hedera.services.records.RecordCacheFactory;
import com.hederahashgraph.fee.CryptoFeeBuilder;
import com.hederahashgraph.fee.FeeObject;
import com.hederahashgraph.fee.FileFeeBuilder;
import com.hederahashgraph.fee.SmartContractFeeBuilder;

import java.util.HashMap;
import java.util.List;

import static com.hedera.test.mocks.TestExchangeRates.TEST_EXCHANGE;
import static com.hedera.test.mocks.TestUsagePricesProvider.TEST_USAGE_PRICES;

public enum TestFeesFactory {
	FEES_FACTORY;

	public FeeCalculator get() {
		return getWithExchange(TEST_EXCHANGE);
	}

	public FeeCalculator getWithExchange(HbarCentExchange exchange) {
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
				properties,
				exchange,
				TEST_USAGE_PRICES,
				List.of(
						/* Crypto */
						new CryptoCreateResourceUsage(cryptoFees),
						new CryptoDeleteResourceUsage(cryptoFees),
						new CryptoUpdateResourceUsage(cryptoFees),
						new CryptoTransferResourceUsage(cryptoFees),
						/* Contract */
						new ContractCallResourceUsage(contractFees),
						new ContractCreateResourceUsage(contractFees),
						new ContractDeleteResourceUsage(contractFees),
						new ContractUpdateResourceUsage(contractFees),
						/* File */
						new FileCreateResourceUsage(fileFees),
						new FileDeleteResourceUsage(fileFees),
						new FileUpdateResourceUsage(),
						new FileAppendResourceUsage(fileFees),
						new SystemDeleteFileResourceUsage(fileFees),
						new SystemUndeleteFileResourceUsage(fileFees),
						/* Consensus */
						new CreateTopicResourceUsage(),
						new UpdateTopicResourceUsage(),
						new DeleteTopicResourceUsage(),
						new SubmitMessageResourceUsage(),
						/* System */
						new FreezeResourceUsage()
				),
				List.of(
						/* Meta */
						new GetTxnRecordResourceUsage(recordCache, answerFunctions, cryptoFees),
						/* Crypto */
						new GetAccountInfoResourceUsage(cryptoFees),
						new GetAccountRecordsResourceUsage(answerFunctions, cryptoFees),
						/* Consensus */
						new GetTopicInfoResourceUsage()
				)
		);
	}
}
