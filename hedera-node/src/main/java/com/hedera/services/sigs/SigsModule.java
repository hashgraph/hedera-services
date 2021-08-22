package com.hedera.services.sigs;

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.NodeLocalProperties;
import com.hedera.services.files.HederaFs;
import com.hedera.services.keys.OnlyIfSigVerifiableValid;
import com.hedera.services.ledger.accounts.BackingStore;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.sigs.annotations.HandleSigReqs;
import com.hedera.services.sigs.annotations.QuerySigReqs;
import com.hedera.services.sigs.order.SigRequirements;
import com.hedera.services.sigs.order.SignatureWaivers;
import com.hedera.services.sigs.verification.SyncVerifier;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleTopic;
import com.hedera.services.stats.MiscRunningAvgs;
import com.hedera.services.stats.MiscSpeedometers;
import com.hedera.services.store.schedule.ScheduleStore;
import com.hedera.services.store.tokens.TokenStore;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.common.Platform;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.fcmap.FCMap;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;
import java.util.function.BiPredicate;
import java.util.function.Supplier;

import static com.hedera.services.sigs.metadata.DelegatingSigMetadataLookup.backedLookupsFor;
import static com.hedera.services.sigs.metadata.DelegatingSigMetadataLookup.defaultAccountRetryingLookupsFor;
import static com.hedera.services.sigs.metadata.SigMetadataLookup.REF_LOOKUP_FACTORY;
import static com.hedera.services.sigs.metadata.SigMetadataLookup.SCHEDULE_REF_LOOKUP_FACTORY;

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

	@Provides
	@Singleton
	@HandleSigReqs
	public static SigRequirements provideHandleSigReqs(
			HederaFs hfs,
			TokenStore tokenStore,
			ScheduleStore scheduleStore,
			SignatureWaivers signatureWaivers,
			GlobalDynamicProperties dynamicProperties,
			BackingStore<AccountID, MerkleAccount> backingAccounts,
			Supplier<FCMap<MerkleEntityId, MerkleTopic>> topics,
			Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts
	) {
		final var sigMetaLookup = backedLookupsFor(
				hfs,
				backingAccounts,
				topics,
				accounts,
				REF_LOOKUP_FACTORY.apply(tokenStore),
				SCHEDULE_REF_LOOKUP_FACTORY.apply(scheduleStore));
		return new SigRequirements(sigMetaLookup, dynamicProperties, signatureWaivers);
	}

	@Provides
	@Singleton
	@QuerySigReqs
	public static SigRequirements provideQuerySigReqs(
			HederaFs hfs,
			TokenStore tokenStore,
			ScheduleStore scheduleStore,
			SignatureWaivers signatureWaivers,
			MiscRunningAvgs runningAvgs,
			MiscSpeedometers speedometers,
			NodeLocalProperties nodeLocalProperties,
			GlobalDynamicProperties dynamicProperties,
			Supplier<FCMap<MerkleEntityId, MerkleTopic>> topics,
			Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts
	) {
		final var sigMetaLookup = defaultAccountRetryingLookupsFor(
				hfs,
				nodeLocalProperties,
				accounts,
				topics,
				REF_LOOKUP_FACTORY.apply(tokenStore),
				SCHEDULE_REF_LOOKUP_FACTORY.apply(scheduleStore),
				runningAvgs,
				speedometers);
		return new SigRequirements(sigMetaLookup, dynamicProperties, signatureWaivers);
	}
}
