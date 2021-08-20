package com.hedera.services.sigs;

import com.hedera.services.keys.OnlyIfSigVerifiableValid;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.sigs.verification.SyncVerifier;
import com.swirlds.common.Platform;
import com.swirlds.common.crypto.TransactionSignature;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;
import java.util.function.BiPredicate;

@Module
public abstract class SigsModule {
	@Provides
	@Singleton
	public static SyncVerifier provideSyncVerifier(Platform platform) {
		return platform.getCryptography()::verifySync;
	}

	@Provides
	@Singleton
	public static BiPredicate<JKey, TransactionSignature> provideValidityTest(SyncVerifier syncVerifier) {
		return new OnlyIfSigVerifiableValid(syncVerifier);
	}
}
