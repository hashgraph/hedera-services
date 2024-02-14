/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.services.yahcli.suites;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.queries.QueryVerbs;
import com.hedera.services.bdd.spec.transactions.TxnVerbs;
import com.hedera.services.bdd.spec.transactions.token.TokenMovement;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.yahcli.commands.validation.ValidationCommand;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenFreezeStatus;
import com.hederahashgraph.api.proto.java.TokenKycStatus;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class TokenValidationSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(TokenValidationSuite.class);

    private final Map<String, String> specConfig;

    public TokenValidationSuite(Map<String, String> specConfig) {
        this.specConfig = specConfig;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(new HapiSpec[] {
            validateTokens(),
        });
    }

    private HapiSpec validateTokens() {
        AtomicLong initialTreasuryBalance = new AtomicLong();
        return HapiSpec.customHapiSpec("validateTokens")
                .withProperties(specConfig)
                .given(
                        QueryVerbs.getTokenInfo(ValidationCommand.TOKEN)
                                .payingWith(ValidationCommand.PAYER)
                                .hasName("Hedera Post-Update Validation Token")
                                .hasSymbol("TACOCAT")
                                .hasTreasury(ValidationCommand.TREASURY)
                                .hasFreezeDefault(TokenFreezeStatus.Unfrozen)
                                .hasKycDefault(TokenKycStatus.Revoked)
                                .hasWipeKey(ValidationCommand.TOKEN)
                                .hasSupplyKey(ValidationCommand.TOKEN)
                                .hasFreezeKey(ValidationCommand.TOKEN)
                                .hasAdminKey(ValidationCommand.TOKEN)
                                .hasKycKey(ValidationCommand.TOKEN),
                        QueryVerbs.getAccountBalance(ValidationCommand.TREASURY)
                                .payingWith(ValidationCommand.PAYER)
                                .savingTokenBalance(ValidationCommand.TOKEN, initialTreasuryBalance::set),
                        UtilVerbs.logIt(ValidationCommand.checkBoxed("Token entities look good")))
                .when(
                        TxnVerbs.tokenDissociate(ValidationCommand.RECEIVER, ValidationCommand.TOKEN)
                                .payingWith(ValidationCommand.PAYER)
                                .hasKnownStatusFrom(
                                        ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT, ResponseCodeEnum.SUCCESS),
                        TxnVerbs.tokenAssociate(ValidationCommand.RECEIVER, ValidationCommand.TOKEN)
                                .payingWith(ValidationCommand.PAYER),
                        TxnVerbs.tokenFreeze(ValidationCommand.TOKEN, ValidationCommand.RECEIVER)
                                .payingWith(ValidationCommand.PAYER),
                        TxnVerbs.cryptoTransfer(TokenMovement.moving(1, ValidationCommand.TOKEN)
                                        .between(ValidationCommand.TREASURY, ValidationCommand.RECEIVER))
                                .payingWith(ValidationCommand.PAYER)
                                .hasKnownStatus(ResponseCodeEnum.ACCOUNT_FROZEN_FOR_TOKEN),
                        TxnVerbs.tokenUnfreeze(ValidationCommand.TOKEN, ValidationCommand.RECEIVER)
                                .payingWith(ValidationCommand.PAYER),
                        TxnVerbs.cryptoTransfer(TokenMovement.moving(1, ValidationCommand.TOKEN)
                                        .between(ValidationCommand.TREASURY, ValidationCommand.RECEIVER))
                                .payingWith(ValidationCommand.PAYER)
                                .hasKnownStatus(ResponseCodeEnum.ACCOUNT_KYC_NOT_GRANTED_FOR_TOKEN),
                        TxnVerbs.grantTokenKyc(ValidationCommand.TOKEN, ValidationCommand.RECEIVER)
                                .payingWith(ValidationCommand.PAYER),
                        TxnVerbs.mintToken(ValidationCommand.TOKEN, 2).payingWith(ValidationCommand.PAYER),
                        TxnVerbs.cryptoTransfer(TokenMovement.moving(1, ValidationCommand.TOKEN)
                                        .between(ValidationCommand.TREASURY, ValidationCommand.RECEIVER))
                                .payingWith(ValidationCommand.PAYER),
                        UtilVerbs.logIt(ValidationCommand.checkBoxed("Token management looks good")))
                .then(
                        QueryVerbs.getAccountBalance(ValidationCommand.RECEIVER)
                                .payingWith(ValidationCommand.PAYER)
                                .hasTokenBalance(ValidationCommand.TOKEN, 1L),
                        UtilVerbs.sourcing(() -> QueryVerbs.getAccountBalance(ValidationCommand.TREASURY)
                                .payingWith(ValidationCommand.PAYER)
                                .hasTokenBalance(ValidationCommand.TOKEN, 1L + initialTreasuryBalance.get())),
                        TxnVerbs.wipeTokenAccount(ValidationCommand.TOKEN, ValidationCommand.RECEIVER, 1)
                                .payingWith(ValidationCommand.PAYER),
                        TxnVerbs.burnToken(ValidationCommand.TOKEN, 1L).payingWith(ValidationCommand.PAYER),
                        QueryVerbs.getAccountBalance(ValidationCommand.RECEIVER)
                                .payingWith(ValidationCommand.PAYER)
                                .hasTokenBalance(ValidationCommand.TOKEN, 0L),
                        UtilVerbs.sourcing(() -> QueryVerbs.getAccountBalance(ValidationCommand.TREASURY)
                                .payingWith(ValidationCommand.PAYER)
                                .hasTokenBalance(ValidationCommand.TOKEN, initialTreasuryBalance.get())),
                        UtilVerbs.logIt(ValidationCommand.checkBoxed("Token balance changes looks good")));
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
