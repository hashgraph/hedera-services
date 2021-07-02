package com.hedera.test.factories.scenarios;

import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.utils.PlatformTxnAccessor;

import static com.hedera.services.state.submerkle.EntityId.MISSING_ENTITY_ID;
import static com.hedera.services.state.submerkle.FcCustomFee.fixedFee;
import static com.hedera.test.factories.txns.PlatformTxnFactory.from;
import static com.hedera.test.factories.txns.TokenFeeScheduleUpdateFactory.newSignedTokenFeeScheduleUpdate;

public enum TokenFeeScheduleUpdateScenarios implements TxnHandlingScenario {
	UPDATE_TOKEN_FEE_SCHEDULE_BUT_TOKEN_DOESNT_EXIST {
		@Override
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedTokenFeeScheduleUpdate()
							.updating(MISSING_TOKEN)
							.withCustom(fixedFee(1, null, MISSING_ENTITY_ID))
							.get()
			));
		}
	},
	UPDATE_TOKEN_WITH_NO_FEE_SCHEDULE_KEY {
		@Override
		public PlatformTxnAccessor platformTxn() throws Throwable {
			return new PlatformTxnAccessor(from(
					newSignedTokenFeeScheduleUpdate()
							.updating(KNOWN_TOKEN_NO_SPECIAL_KEYS)
							.withCustom(fixedFee(1, null, MISSING_ENTITY_ID))
							.get()
			));
		}
	},
	UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_NO_FEE_COLLECTOR_SIG_REQ {
		@Override
		public PlatformTxnAccessor platformTxn() throws Throwable {
			final var feeCollectorNoSigReq = EntityId.fromGrpcAccountId(NO_RECEIVER_SIG);
			return new PlatformTxnAccessor(from(
					newSignedTokenFeeScheduleUpdate()
							.updating(KNOWN_TOKEN_WITH_FEE_SCHEDULE_KEY)
							.withCustom(fixedFee(1, null, feeCollectorNoSigReq))
							.get()
			));
		}
	},
	UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_WITH_FEE_COLLECTOR_SIG_REQ {
		@Override
		public PlatformTxnAccessor platformTxn() throws Throwable {
			final var feeCollectorNoSigReq = EntityId.fromGrpcAccountId(NO_RECEIVER_SIG);
			final var feeCollectorWithSigReq = EntityId.fromGrpcAccountId(RECEIVER_SIG);
			return new PlatformTxnAccessor(from(
					newSignedTokenFeeScheduleUpdate()
							.updating(KNOWN_TOKEN_WITH_FEE_SCHEDULE_KEY)
							.withCustom(fixedFee(1, null, feeCollectorNoSigReq))
							.withCustom(fixedFee(2, null, feeCollectorWithSigReq))
							.get()
			));
		}
	},
	UPDATE_TOKEN_WITH_FEE_SCHEDULE_KEY_WITH_MISSING_FEE_COLLECTOR{
		@Override
		public PlatformTxnAccessor platformTxn() throws Throwable {
			final var missingFeeCollector = EntityId.fromGrpcAccountId(MISSING_ACCOUNT);
			return new PlatformTxnAccessor(from(
					newSignedTokenFeeScheduleUpdate()
							.updating(KNOWN_TOKEN_WITH_FEE_SCHEDULE_KEY)
							.withCustom(fixedFee(1, null, missingFeeCollector))
							.get()
			));
		}
	},
}
