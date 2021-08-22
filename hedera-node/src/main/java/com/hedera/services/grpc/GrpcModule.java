package com.hedera.services.grpc;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.grpc.marshalling.AdjustmentUtils;
import com.hedera.services.grpc.marshalling.BalanceChangeManager;
import com.hedera.services.grpc.marshalling.CustomSchedulesManager;
import com.hedera.services.grpc.marshalling.FeeAssessor;
import com.hedera.services.grpc.marshalling.FixedFeeAssessor;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.grpc.marshalling.RoyaltyFeeAssessor;
import com.hedera.services.ledger.PureTransferSemanticChecks;
import com.hedera.services.txns.customfees.CustomFeeSchedules;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;

@Module
public abstract class GrpcModule {
	@Provides @Singleton
	public static RoyaltyFeeAssessor provideRoyaltyFeeAssessor(FixedFeeAssessor fixedFeeAssessor) {
		return new RoyaltyFeeAssessor(fixedFeeAssessor, AdjustmentUtils::adjustedChange);
	}

	@Provides @Singleton
	public static ImpliedTransfersMarshal provideImpliedTransfersMarshal(
			FeeAssessor feeAssessor,
			CustomFeeSchedules customFeeSchedules,
			GlobalDynamicProperties dynamicProperties,
			PureTransferSemanticChecks transferSemanticChecks
	) {
		return new ImpliedTransfersMarshal(
				feeAssessor,
				customFeeSchedules,
				dynamicProperties,
				transferSemanticChecks,
				BalanceChangeManager::new,
				CustomSchedulesManager::new);
	}
}
