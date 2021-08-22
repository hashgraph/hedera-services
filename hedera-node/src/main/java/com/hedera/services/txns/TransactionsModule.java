package com.hedera.services.txns;

import com.hedera.services.txns.crypto.CryptoLogicModule;
import com.hedera.services.txns.customfees.CustomFeeSchedules;
import com.hedera.services.txns.customfees.FcmCustomFeeSchedules;
import com.hedera.services.txns.file.FileLogicModule;
import com.hedera.services.txns.validation.ContextOptionValidator;
import com.hedera.services.txns.validation.OptionValidator;
import dagger.Binds;
import dagger.Module;

import javax.inject.Singleton;

@Module(includes = {
		FileLogicModule.class,
		CryptoLogicModule.class,
})
public abstract class TransactionsModule {
	@Binds
	@Singleton
	public abstract OptionValidator bindOptionValidator(ContextOptionValidator contextOptionValidator);

	@Binds
	@Singleton
	public abstract CustomFeeSchedules bindCustomFeeSchedules(FcmCustomFeeSchedules fcmCustomFeeSchedules);
}
