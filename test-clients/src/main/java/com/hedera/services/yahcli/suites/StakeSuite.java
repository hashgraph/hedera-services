package com.hedera.services.yahcli.suites;


import com.hedera.services.bdd.spec.HapiApiSpec;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoUpdate;
import com.hedera.services.bdd.suites.HapiApiSuite;
import com.hedera.services.yahcli.config.ConfigManager;
import com.hedera.services.yahcli.config.ConfigUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

import static com.hedera.services.bdd.spec.HapiApiSpec.customHapiSpec;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.keyFromFile;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.noOp;
import static com.hedera.services.yahcli.config.ConfigUtils.uncheckedKeyFileFor;

public class StakeSuite extends HapiApiSuite {
    private static final Logger log = LogManager.getLogger(StakeSuite.class);
    private static final String STAKER_KEY_IF_NEEDED = "STAKER_KEY";

    public enum TargetType {NODE, ACCOUNT, NONE}

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
        this.stakingAccount = stakingAccount != null
                ? Utils.extractAccount(stakingAccount)
                : null;
        this.configManager = configManager;
    }

    @Override
    public List<HapiApiSpec> getSpecsInSuite() {
        return List.of(doStake());
    }

    private HapiSpecOperation importKeyIfNecessary() {
        if (stakingAccount == null) {
            return noOp();
        } else {
            return keyFromFile(
                    STAKER_KEY_IF_NEEDED,
                    uncheckedKeyFileFor(
                                    configManager.keysLoc(),
                                    "account" + numberOf(stakingAccount))
                            .getAbsolutePath()
            ).yahcliLogged();
        }
    }

    private long numberOf(final String id) {
        return Long.parseLong(id.substring(id.lastIndexOf(".") + 1));
    }

    private HapiApiSpec doStake() {
        final var toUpdate = stakingAccount == null
                ? DEFAULT_PAYER : stakingAccount;
        return customHapiSpec("DoStake")
                .withProperties(specConfig).given(
                        importKeyIfNecessary()
                ).when().then(
                        customized(cryptoUpdate(toUpdate)
                                .blankMemo()
                                .withYahcliLogging())
                );
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
            baseUpdate.signedBy(DEFAULT_PAYER);
        } else {
            baseUpdate.signedBy(DEFAULT_PAYER, STAKER_KEY_IF_NEEDED);
        }
        return baseUpdate.fee(ONE_HBAR);
    }

    @Override
    protected Logger getResultsLogger() {
        return log;
    }
}
