package com.hedera.services.bdd.suites.validation;

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
import com.hedera.services.bdd.spec.persistence.Account;
import com.hedera.services.bdd.spec.persistence.Entity;
import com.hedera.services.bdd.spec.persistence.PemKey;
import com.hedera.services.bdd.spec.persistence.Token;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenKycStatus;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.function.Supplier;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getAccountInfo;
import static com.hedera.services.bdd.spec.queries.crypto.ExpectedTokenRel.relationshipWith;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.grantTokenKyc;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenAssociate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUnfreeze;
import static com.hedera.services.bdd.spec.transactions.token.TokenMovement.moving;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.ensureDissociated;
import static com.hedera.services.bdd.suites.validation.YamlHelper.serializeEntity;
import static com.hedera.services.bdd.suites.validation.YamlHelper.yaml;

public class TokenPuvSuite extends HapiApiSuite {
	private static final Logger log = LogManager.getLogger(TokenPuvSuite.class);

	private final MiscConfig miscConfig;
	private final NetworkConfig targetInfo;

	public TokenPuvSuite(MiscConfig miscConfig, NetworkConfig targetInfo) {
		this.miscConfig = miscConfig;
		this.targetInfo = targetInfo;
	}

	@Override
	protected List<HapiApiSpec> getSpecsInSuite() {
		return List.of(new HapiApiSpec[] {
				cleanupIfNecessary(),
				initialAssociation(),
				initialFunding(),
		});
	}

	private HapiApiSpec initialFunding() {
		return HapiApiSpec.customHapiSpec("InitialFunding").withProperties(
				targetInfo.toCustomProperties(miscConfig)
		).given(
				cryptoTransfer(moving(Amounts.BESTOWED_CAT_TOKENS, Names.CAT_TOKEN)
						.between(Names.TREASURY, Names.CAT_BENEFICIARY)),
				cryptoTransfer(moving(Amounts.BESTOWED_TACO_TOKENS, Names.TACO_TOKEN)
						.between(Names.TREASURY, Names.TACO_BENEFICIARY))
						.hasKnownStatus(ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN)
		).when(
				tokenUnfreeze(Names.TACO_TOKEN, Names.TACO_BENEFICIARY),
				cryptoTransfer(moving(Amounts.BESTOWED_TACO_TOKENS, Names.TACO_TOKEN)
						.between(Names.TREASURY, Names.TACO_BENEFICIARY))
						.hasKnownStatus(ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN),
				grantTokenKyc(Names.TACO_TOKEN, Names.TACO_BENEFICIARY),
				cryptoTransfer(moving(Amounts.BESTOWED_TACO_TOKENS, Names.TACO_TOKEN)
						.between(Names.TREASURY, Names.TACO_BENEFICIARY))
		).then(
				getAccountInfo(Names.TACO_BENEFICIARY)
						.hasToken(relationshipWith(Names.TACO_TOKEN)
								.freeze(TokenFreezeStatus.Unfrozen)
								.kyc(TokenKycStatus.Granted)
								.balance(Amounts.BESTOWED_TACO_TOKENS)),
				getAccountInfo(Names.CAT_BENEFICIARY)
						.hasToken(relationshipWith(Names.CAT_TOKEN)
								.freeze(TokenFreezeStatus.FreezeNotApplicable)
								.kyc(TokenKycStatus.KycNotApplicable)
								.balance(Amounts.BESTOWED_CAT_TOKENS))
		);
	}

	private HapiApiSpec initialAssociation() {
		return HapiApiSpec.customHapiSpec("InitialAssociation").withProperties(
				targetInfo.toCustomProperties(miscConfig)
		).given(
				tokenAssociate(Names.CAT_BENEFICIARY, Names.CAT_TOKEN),
				tokenAssociate(Names.TACO_BENEFICIARY, Names.TACO_TOKEN)
		).when().then(
				getAccountInfo(Names.TACO_BENEFICIARY)
						.hasToken(relationshipWith(Names.TACO_TOKEN)
								.freeze(TokenFreezeStatus.Frozen)
								.kyc(TokenKycStatus.Revoked)),
				getAccountInfo(Names.CAT_BENEFICIARY)
						.hasToken(relationshipWith(Names.CAT_TOKEN)
								.freeze(TokenFreezeStatus.FreezeNotApplicable)
								.kyc(TokenKycStatus.KycNotApplicable))
		);
	}

	private HapiApiSpec cleanupIfNecessary() {
		return HapiApiSpec.customHapiSpec("CleanupIfNecessary").withProperties(
				targetInfo.toCustomProperties(miscConfig)
		).given().when().then(
				ensureDissociated(Names.CAT_BENEFICIARY, List.of(Names.CAT_TOKEN, Names.TACO_TOKEN))
		);
	}

	public void initEntitiesIfNeeded() {
		var entitiesLoc = targetInfo.effPersistentEntitiesDir();
		try {
			Files.createDirectories(Paths.get(entitiesLoc, "keys"));
		} catch (IOException e) {
			log.error("Could not initialize persistent entities dir '{}'!", entitiesLoc);
			throw new UncheckedIOException(e);
		}
		ensure(entitiesLoc, Names.CAT_TOKEN, this::catToken);
		ensure(entitiesLoc, Names.TACO_TOKEN, this::tacoToken);
		ensure(entitiesLoc, Names.TREASURY, this::treasury);
		ensure(entitiesLoc, Names.CAT_BENEFICIARY, this::catBeneficiary);
		ensure(entitiesLoc, Names.TACO_BENEFICIARY, this::tacoBeneficiary);
	}

	@Override
	protected Logger getResultsLogger() {
		return log;
	}

	private void ensure(String entitiesDir, String name, Supplier<Entity> factory) {
		var f = Paths.get(entitiesDir, yaml(name)).toFile();
		if (f.exists()) {
			return;
		}
		serializeEntity(factory.get(), f.getAbsolutePath());
	}

	private Entity catToken() {
		var token = new Token();
		token.setName("PostUpdateValidation Cat Token");
		token.setAdminKey(PemKey.prefixedAt(Names.CAT_TOKEN_ADMIN));
		token.setSymbol("CAT");
		token.setTreasury(Names.TREASURY);
		return Entity.from(Names.CAT_TOKEN, token);
	}

	private Entity tacoToken() {
		var token = new Token();
		token.setKycKey(PemKey.prefixedAt(Names.TACO_TOKEN_KYC));
		token.setWipeKey(PemKey.prefixedAt(Names.TACO_TOKEN_WIPE));
		token.setAdminKey(PemKey.prefixedAt(Names.TACO_TOKEN_ADMIN));
		token.setSupplyKey(PemKey.prefixedAt(Names.TACO_TOKEN_SUPPLY));
		token.setFreezeKey(PemKey.prefixedAt(Names.TACO_TOKEN_FREEZE));
		token.setName("PostUpdateValidation Taco Token");
		token.setSymbol("TACO");
		token.setTreasury(Names.TREASURY);
		return Entity.from(Names.TACO_TOKEN, token);
	}

	private Entity treasury() {
		var treasury = new Account();
		treasury.setKey(PemKey.prefixedAt(Names.TREASURY));
		return Entity.from(Names.TREASURY, treasury);
	}

	private Entity catBeneficiary() {
		var bene = new Account();
		bene.setKey(PemKey.prefixedAt(Names.CAT_BENEFICIARY));
		return Entity.from(Names.CAT_BENEFICIARY, bene);
	}

	private Entity tacoBeneficiary() {
		var bene = new Account();
		bene.setKey(PemKey.prefixedAt(Names.TACO_BENEFICIARY));
		return Entity.from(Names.TACO_BENEFICIARY, bene);
	}

	static class Names {
		static final String CAT_TOKEN = "puvCatToken";
		static final String CAT_TOKEN_ADMIN = "puvCatTokenAdmin";
		static final String TACO_TOKEN = "puvTacoToken";
		static final String TACO_TOKEN_KYC = "puvTacoTokenKyc";
		static final String TACO_TOKEN_WIPE = "puvTacoTokenWipe";
		static final String TACO_TOKEN_ADMIN = "puvTacoTokenAdmin";
		static final String TACO_TOKEN_SUPPLY = "puvTacoTokenSupply";
		static final String TACO_TOKEN_FREEZE = "puvTacoTokenFreeze";
		static final String TREASURY = "puvTreasury";
		static final String CAT_BENEFICIARY = "puvCatBeneficiary";
		static final String TACO_BENEFICIARY = "puvTacoBeneficiary";
	}

	static class Amounts {
		static final long BESTOWED_CAT_TOKENS = 123;
		static final long BESTOWED_TACO_TOKENS = 456;
	}
}
