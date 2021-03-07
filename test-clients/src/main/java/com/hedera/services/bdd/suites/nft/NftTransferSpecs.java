package com.hedera.services.bdd.suites.nft;

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
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;

import static com.hedera.services.bdd.spec.HapiApiSpec.defaultHapiSpec;
import static com.hedera.services.bdd.spec.keys.KeyShape.listOf;
import static com.hedera.services.bdd.spec.keys.KeyShape.threshOf;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountBalance;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getFileInfo;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTxnRecord;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.fileDelete;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nftAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nftCreate;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.nft.Acquisition.ofNft;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.someCivilian;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_SIGNATURE;

public class NftTransferSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(NftTransferSpecs.class);

	public static void main(String... args) {
		new NftTransferSpecs().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				expectedSigsRequired(),
		});
	}

	private HapiApiSpec expectedSigsRequired() {
		String nftType = "naturalHistory";

		return defaultHapiSpec("ExpectedSigsRequired")
				.given(
						someCivilian(),
						cryptoCreate("Smithsonian")
								.emptyBalance(),
						cryptoCreate("buyer")
								.receiverSigRequired(true)
								.emptyBalance(),
						nftCreate(nftType)
								.treasury("Smithsonian")
								.initialSerialNos(1)
				).when(
						nftAssociate("buyer", nftType),
						cryptoTransfer()
								.changingOwnership(
										ofNft(nftType).serialNo("SN1").from("Smithsonian").to("buyer")
								).payingWith(CIVILIAN)
								.signedBy(CIVILIAN)
								.hasKnownStatus(INVALID_SIGNATURE),
						cryptoTransfer()
								.changingOwnership(
										ofNft(nftType).serialNo("SN1").from("Smithsonian").to("buyer")
								).payingWith(CIVILIAN)
								.signedBy(CIVILIAN, "Smithsonian")
								.hasKnownStatus(INVALID_SIGNATURE)
				).then(
						cryptoTransfer()
								.changingOwnership(
										ofNft(nftType).serialNo("SN0").from("Smithsonian").to("buyer")
								).payingWith(CIVILIAN)
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
