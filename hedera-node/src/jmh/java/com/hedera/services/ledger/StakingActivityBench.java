package com.hedera.services.ledger;

import static com.hedera.services.ledger.properties.AccountProperty.BALANCE;
import static com.hedera.services.ledger.properties.AccountProperty.STAKED_ID;
import static com.hedera.services.setup.Constructables.FIRST_NODE_I;
import static com.hedera.services.setup.Constructables.FIRST_USER_I;
import static com.hedera.services.setup.Constructables.FUNDING_ID;
import static com.hedera.services.setup.Constructables.PRETEND_AMOUNT;
import static com.hedera.services.setup.Constructables.PRETEND_FEE;
import static com.hedera.services.setup.Constructables.SECS_PER_DAY;
import static com.hedera.services.setup.Constructables.SOME_TIME;
import static com.hedera.services.setup.InfrastructureInitializer.initializeStakeableAccounts;
import static com.hedera.services.setup.InfrastructureManager.loadOrCreateBundle;
import static com.hedera.services.setup.InfrastructureType.ACCOUNTS_MM;
import static com.hedera.services.setup.InfrastructureType.STAKING_INFOS_MM;

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
    private long n;
    private int round;
    private AccountID[] ids;

    @Param("3")
    int numNodes;

    @Param("1000")
    int stakeableAccounts;

    @Param("10000")
    int actionsPerRound;

    @Param("10")
    int roundsPerPeriod;

    @Param("5")
    int meanDaysBeforeNewStakePeriodStart;

    @Param("0.50")
    double stakeToNodeProb;

    @Param("0.40")
    double stakeToAccountProb;

    private int callsBetweenStakingChange;
    private StakingActivityApp app;
    private InfrastructureBundle bundle;
    private TransactionalLedger<AccountID, AccountProperty, MerkleAccount> ledger;

    @Setup(Level.Trial)
    public void setupInfrastructure() {
        Constructables.registerForMerkleMap();
        Constructables.registerForAccounts();
        Constructables.registerForStakingInfo();

        bundle = loadOrCreateBundle(activeConfig(), requiredInfra());
        app = DaggerStakingActivityApp.builder().bundle(bundle).build();
        ledger = app.stakingLedger();

        ids = new AccountID[stakeableAccounts + 1001];
        for (int j = 1; j < stakeableAccounts + 1001; j++) {
            ids[j] = AccountID.newBuilder().setAccountNum(j).build();
        }
        i = 0;
        n = 0;

        initializeStakeableAccounts(
                new SplittableRandom(Constructables.SEED),
                activeConfig(),
                app.backingAccounts(),
                app.stakingInfos().get(),
                ledger);

        app.networkCtx().get().setStakingRewardsActivated(true);

        final var actionsPerDay = (long) actionsPerRound * roundsPerPeriod;
        final var avgDailyActionsPerAccount = actionsPerDay / stakeableAccounts;
        callsBetweenStakingChange =
                (int) (meanDaysBeforeNewStakePeriodStart * avgDailyActionsPerAccount);
        System.out.println(
                "A staking change will occur every ~"
                        + callsBetweenStakingChange
                        + " benchmark invocations");
    }

    @Setup(Level.Invocation)
    public void simulateRoundBoundary() {
        if (n > 0 && n % actionsPerRound == 0) {
            bundle.newRound();
            round++;
            if (round % roundsPerPeriod == 0) {
                final var period = round / roundsPerPeriod;
                final var now = SOME_TIME.plusSeconds(period * SECS_PER_DAY);
                app.txnCtx().resetFor(null, now, 0L);
                app.endOfPeriodCalcs().updateNodes(now);
                System.out.println("--- END OF PERIOD @ " + now + " ---");
                System.out.println(app.stakingInfos().get());
            }
        }
    }

    @Benchmark
    public void stakingActivities() {
        advanceI();
        ledger.begin();
        // Also the staker in the case of a changed staking election
        final var payerId = advanceToNextId();
        // Also the stakee in the case of a changed staking election
        final var countpartyId = advanceToNextId();
        final var nodeI = Math.floorMod(i, numNodes);
        ledger.set(FUNDING_ID, BALANCE, (long) ledger.get(FUNDING_ID, BALANCE) + PRETEND_FEE / 2);
        final var nodeId = ids[FIRST_NODE_I + nodeI];
        ledger.set(nodeId, BALANCE, (long) ledger.get(nodeId, BALANCE) + PRETEND_FEE / 2);
        if (n % callsBetweenStakingChange == 0) {
            ledger.set(payerId, BALANCE, (long) ledger.get(payerId, BALANCE) - PRETEND_FEE);
            if (n % 3 == 0) {
                if (!payerId.equals(countpartyId)) {
                    ledger.set(payerId, STAKED_ID, countpartyId.getAccountNum());
                }
            } else {
                ledger.set(payerId, STAKED_ID, -nodeI - 1L);
            }
        } else {
            ledger.set(
                    payerId,
                    BALANCE,
                    (long) ledger.get(payerId, BALANCE) - PRETEND_FEE - PRETEND_AMOUNT);
            ledger.set(
                    countpartyId,
                    BALANCE,
                    (long) ledger.get(countpartyId, BALANCE) + PRETEND_AMOUNT);
        }
        ledger.commit();

        n++;
    }

    private AccountID advanceToNextId() {
        return ids[FIRST_USER_I + Math.floorMod(i, stakeableAccounts)];
    }

    private void advanceI() {
        i = i * Constructables.MULTIPLIER + Constructables.ADDEND;
    }

    // --- Helpers ---
    private Map<String, Object> activeConfig() {
        return Map.of(
                "stakeableAccounts", stakeableAccounts,
                "stakeToNodeProb", stakeToNodeProb,
                "stakeToAccountProb", stakeToAccountProb,
                "nodeIds", numNodes);
    }

    private List<InfrastructureType> requiredInfra() {
        return List.of(ACCOUNTS_MM, STAKING_INFOS_MM);
    }
}
