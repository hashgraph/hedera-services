package com.hedera.services.txns;

import com.hedera.services.txns.validation.ContextOptionValidator;
import com.hedera.services.txns.validation.OptionValidator;
import dagger.Binds;
import dagger.Module;

import javax.inject.Singleton;

@Module
public abstract class LogicModule {
	@Binds @Singleton
	public abstract OptionValidator bindOptionValidator(ContextOptionValidator contextOptionValidator);
}
