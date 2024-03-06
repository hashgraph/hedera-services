/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoUpdate;
import com.hedera.services.bdd.spec.utilops.UtilVerbs;
import com.hedera.services.bdd.suites.HapiSuite;
import com.hedera.services.yahcli.config.ConfigManager;
import com.hedera.services.yahcli.config.ConfigUtils;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StakeSuite extends HapiSuite {
    private static final Logger log = LogManager.getLogger(StakeSuite.class);
    private static final String STAKER_KEY_IF_NEEDED = "STAKER_KEY";

    public enum TargetType {
        NODE,
        ACCOUNT,
        NONE
    }

    private final ConfigManager configManager;
    private final Map<String, String> specConfig;
    private final String stakingAccount;
    private final String target;
    private final TargetType targetType;
    private final Boolean declineRewards;

    public StakeSuite(
            final ConfigManager configManager,
            final Map<String, String> specConfig,
            final String target,
            final TargetType targetType,
            @Nullable final String stakingAccount,
            @Nullable final Boolean declineRewards) {
        this.specConfig = specConfig;
        this.target = target;
        this.targetType = targetType;
        this.declineRewards = declineRewards;
        this.stakingAccount = stakingAccount != null ? Utils.extractAccount(stakingAccount) : null;
        this.configManager = configManager;
    }

    @Override
    public List<HapiSpec> getSpecsInSuite() {
        return List.of(doStake());
    }

    private HapiSpecOperation importKeyIfNecessary() {
        if (stakingAccount == null) {
            return UtilVerbs.noOp();
        } else {
            return UtilVerbs.keyFromFile(
                            STAKER_KEY_IF_NEEDED,
                            ConfigUtils.uncheckedKeyFileFor(
                                            configManager.keysLoc(), "account" + numberOf(stakingAccount))
                                    .getAbsolutePath())
                    .yahcliLogged();
        }
    }

    private long numberOf(final String id) {
        return Long.parseLong(id.substring(id.lastIndexOf(".") + 1));
    }

    private HapiSpec doStake() {
        final var toUpdate = stakingAccount == null ? HapiSuite.DEFAULT_PAYER : stakingAccount;
        return HapiSpec.customHapiSpec("DoStake")
                .withProperties(specConfig)
                .given(importKeyIfNecessary())
                .when()
                .then(customized(cryptoUpdate(toUpdate).blankMemo().withYahcliLogging()));
    }

    private HapiCryptoUpdate customized(final HapiCryptoUpdate baseUpdate) {
        if (targetType == TargetType.ACCOUNT) {
            baseUpdate.newStakedAccountId(ConfigUtils.asId(target));
        } else if (targetType == TargetType.NODE) {
            baseUpdate.newStakedNodeId(Long.parseLong(target));
        }
        if (declineRewards != null) {
            baseUpdate.newDeclinedReward(declineRewards);
        }
        if (stakingAccount == null) {
            baseUpdate.signedBy(HapiSuite.DEFAULT_PAYER);
        } else {
            baseUpdate.signedBy(HapiSuite.DEFAULT_PAYER, STAKER_KEY_IF_NEEDED);
        }
        return baseUpdate.fee(HapiSuite.ONE_HBAR);
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
