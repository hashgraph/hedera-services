package com.hedera.services.ledger;

import static com.hedera.services.setup.InfrastructureInitializer.initializeStakeableAccounts;
import static com.hedera.services.setup.InfrastructureManager.loadOrCreateBundle;
import static com.hedera.services.setup.InfrastructureType.ACCOUNTS_MM;
import static com.hedera.services.setup.InfrastructureType.STAKING_INFOS_MM;

import com.hedera.services.ledger.accounts.staking.EndOfStakingPeriodCalculator;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.setup.Constructables;
import com.hedera.services.setup.InfrastructureBundle;
import com.hedera.services.setup.InfrastructureType;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hederahashgraph.api.proto.java.AccountID;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

@State(Scope.Benchmark)
@Fork(1)
@Warmup(iterations = 1, time = 10)
@Measurement(iterations = 3, time = 30)
public class StakingActivityBench {
    private int i;
    private int n;
    private AccountID[] ids;

    @Param("39")
    int nodeIds;

    @Param("10000")
    int stakeableAccounts;

    @Param("10000")
    int actionsPerRound;

    @Param("0.50")
    double stakeToNodeProb;

    @Param("0.40")
    double stakeToAccountProb;

    private InfrastructureBundle bundle;
    private EndOfStakingPeriodCalculator endOfPeriodCalcs;
    private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> ledger;

    @Setup(Level.Trial)
    public void setupInfrastructure() {
        Constructables.registerForMerkleMap();
        Constructables.registerForAccounts();
        Constructables.registerForStakingInfo();

        final var app = DaggerStakingActivityApp.builder().bundle(bundle).build();

        bundle = loadOrCreateBundle(activeConfig(), requiredInfra());
        ledger = app.stakingLedger();
        endOfPeriodCalcs = app.endOfPeriodCalcs();

        ids = new AccountID[stakeableAccounts + 1001];
        for (int j = 1; j < stakeableAccounts + 1001; j++) {
            ids[j] = AccountID.newBuilder().setAccountNum(j).build();
        }
        i = n = 0;

        initializeStakeableAccounts(
                new SplittableRandom(Constructables.SEED),
                activeConfig(),
                app.backingAccounts(),
                app.stakingInfos(),
                ledger);
    }

    @Setup(Level.Invocation)
    public void simulateRoundBoundary() {
        if (n > 0 && n % actionsPerRound == 0) {
            bundle.newRound();
        }
    }

    @Benchmark
    public void stakingActivities() {
        ledger.begin();
        ledger.commit();

        n++;
    }

    // --- Helpers ---
    private Map<String, Object> activeConfig() {
        return Map.of(
                "stakeableAccounts", stakeableAccounts,
                "stakeToNodeProb", stakeToNodeProb,
                "stakeToAccountProb", stakeToAccountProb,
                "nodeIds", nodeIds);
    }

    private List<InfrastructureType> requiredInfra() {
        return List.of(ACCOUNTS_MM, STAKING_INFOS_MM);
    }
}
