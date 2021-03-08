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
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.nftMint;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hedera.services.bdd.spec.transactions.nft.Acquisition.ofNft;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.ACCOUNT_NOT_ASSOCIATED_TO_NFT_TYPE;

public class NftManagementSpecs extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(NftManagementSpecs.class);

	String me = "Jon Doe";
	String TheSmithsonian = "Smithsonian";
	String NATURAL_HISTORY = "naturallHistory";

	public static void main(String... args) {
		new NftManagementSpecs().runSuiteSync();
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				simpleNftAssociation(),
				simpleMinting(),
		});
	}

	private HapiApiSpec simpleMinting() {

		return defaultHapiSpec("SimpleMinting")
				.given(
						cryptoCreate(TheSmithsonian),
						nftCreate(NATURAL_HISTORY)
								.memo("NFT with first run minting of three serial numbers.")
								.treasury(TheSmithsonian)
								.initialSerialNos(3),
						getAccountBalance(TheSmithsonian).logged()
				).when(
						nftMint(NATURAL_HISTORY, 7)
				).then(
						getAccountBalance(TheSmithsonian).logged()
				);
	}

	private HapiApiSpec simpleNftAssociation() {
		var me = "Jon Doe";
		var TheSmithsonian = "Smithsonian";
		var NATURAL_HISTORY = "naturallHistory";

		return defaultHapiSpec("SimpleNftAssociation")
				.given(
						cryptoCreate(TheSmithsonian),
						cryptoCreate(me),
						nftCreate(NATURAL_HISTORY)
								.memo("NFT with first run minting of three serial numbers.")
								.treasury(TheSmithsonian)
								.initialSerialNos(3)
				).when(
						cryptoTransfer()
								.changingOwnership(
										ofNft(NATURAL_HISTORY).serialNo("SN1")
												.from(TheSmithsonian).to(me))
								.hasKnownStatus(ACCOUNT_NOT_ASSOCIATED_TO_NFT_TYPE),
						nftAssociate(me, NATURAL_HISTORY)
								.payingWith(me)
								.via("association")
				).then(
						cryptoTransfer()
								.via("acquisition")
								.payingWith(me)
								.memo("Some SN1, please!")
								.changingOwnership(
										ofNft(NATURAL_HISTORY).serialNo("SN1")
												.from(TheSmithsonian).to(me)),
						getTxnRecord("acquisition").logged(),
						getAccountBalance(TheSmithsonian).logged(),
						getAccountBalance(me).logged()
				);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}
}
