package com.hedera.node.app.service.mono.config;

import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.hedera.node.app.spi.numbers.HederaFileNumbers;
import dagger.Binds;
import dagger.Module;

@Module
public interface ConfigModule {
    @Binds
    HederaFileNumbers bindFileNumbers(FileNumbers fileNumbers);

    @Binds
    HederaAccountNumbers bindAccountNumbers(AccountNumbers accountNumbers);
}
