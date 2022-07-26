package com.hedera.services.ledger;

import com.hedera.services.ledger.accounts.staking.EndOfStakingPeriodCalculator;
import com.hedera.services.ledger.backing.BackingStore;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.setup.InfrastructureBundle;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleNetworkContext;
import com.hedera.services.state.merkle.MerkleStakingInfo;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.merkle.map.MerkleMap;
import dagger.BindsInstance;
import dagger.Component;
import java.util.function.Supplier;
import javax.inject.Singleton;

@Singleton
@Component(modules = StakingActivityModule.class)
public interface StakingActivityApp {
  BackingStore<AccountID, MerkleAccount> backingAccounts();
  EndOfStakingPeriodCalculator endOfPeriodCalcs();
  Supplier<MerkleNetworkContext> networkCtx();
  MerkleMap<EntityNum, MerkleStakingInfo> stakingInfos();
  TransactionalLedger<AccountID, AccountProperty, MerkleAccount> stakingLedger();

  @Component.Builder
  interface Builder {
    @BindsInstance
    Builder bundle(InfrastructureBundle bundle);

    StakingActivityApp build();
  }
}
