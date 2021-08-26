package com.hedera.services.context.init;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.hedera.services.ServicesState;
import com.hedera.services.context.ServicesContext;
import com.hedera.services.state.expiry.ExpiryManager;
import com.hedera.services.state.logic.NetworkCtxManager;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.merkle.MerkleUniqueTokenId;
import com.hedera.services.store.tokens.views.UniqTokenViewsManager;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.swirlds.blob.BinaryObjectStore;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.ImmutableHash;
import com.swirlds.common.crypto.RunningHash;
import com.swirlds.fcmap.FCMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class InitializationFlowTest {
	private static final ImmutableHash EMPTY_HASH = new ImmutableHash(new byte[DigestType.SHA_384.digestLength()]);

	@Mock
	private RunningHash runningHash;
	@Mock
	private RecordsRunningHashLeaf runningHashLeaf;
	@Mock
	private FCMap<MerkleEntityId, MerkleToken> tokens;
	@Mock
	private FCMap<MerkleUniqueTokenId, MerkleUniqueToken> uniqueTokens;
	@Mock
	private UniqTokenViewsManager uniqTokenViewsManager;
	@Mock
	private NetworkCtxManager networkCtxManager;
	@Mock
	private ExpiryManager expiryManager;
	@Mock
	private ServicesState state;
	@Mock
	private ServicesContext ctx;
	@Mock
	private BinaryObjectStore blobStore;

	@BeforeEach
	void setUp() {
		InitializationFlow.setBlobStoreSupplier(() -> blobStore);
	}

	@AfterEach
	void cleanup() {
		InitializationFlow.setBlobStoreSupplier(BinaryObjectStore::getInstance);
	}

	@Test
	void initializesContextAsExpectedWhenBlobStoreNotInitializing() {
		final var inOrder = Mockito.inOrder(ctx, uniqTokenViewsManager, expiryManager, networkCtxManager);
		given(state.runningHashLeaf()).willReturn(runningHashLeaf);
		given(state.tokens()).willReturn(tokens);
		given(state.uniqueTokens()).willReturn(uniqueTokens);
		given(runningHashLeaf.getRunningHash()).willReturn(runningHash);
		given(runningHash.getHash()).willReturn(EMPTY_HASH);
		given(ctx.uniqTokenViewsManager()).willReturn(uniqTokenViewsManager);
		given(ctx.expiries()).willReturn(expiryManager);
		given(ctx.networkCtxManager()).willReturn(networkCtxManager);

		InitializationFlow.accept(state, ctx);

		inOrder.verify(ctx).update(state);
		inOrder.verify(ctx).setRecordsInitialHash(EMPTY_HASH);
		inOrder.verify(ctx).rebuildBackingStoresIfPresent();
		inOrder.verify(ctx).rebuildStoreViewsIfPresent();
		inOrder.verify(uniqTokenViewsManager).rebuildNotice(tokens, uniqueTokens);
		inOrder.verify(expiryManager).reviewExistingPayerRecords();
		inOrder.verify(expiryManager).reviewExistingShortLivedEntities();
		inOrder.verify(networkCtxManager).setObservableFilesNotLoaded();
		inOrder.verify(networkCtxManager).loadObservableSysFilesIfNeeded();
	}

	@Test
	void initializesContextAsExpectedWhenBlobStoreInitializing() {
		given(blobStore.isInitializing()).willReturn(true);
		final var inOrder = Mockito.inOrder(ctx, uniqTokenViewsManager, expiryManager, networkCtxManager);

		given(state.runningHashLeaf()).willReturn(runningHashLeaf);
		given(state.tokens()).willReturn(tokens);
		given(state.uniqueTokens()).willReturn(uniqueTokens);
		given(runningHashLeaf.getRunningHash()).willReturn(runningHash);
		given(runningHash.getHash()).willReturn(EMPTY_HASH);
		given(ctx.uniqTokenViewsManager()).willReturn(uniqTokenViewsManager);
		given(ctx.expiries()).willReturn(expiryManager);
		given(ctx.networkCtxManager()).willReturn(networkCtxManager);

		InitializationFlow.accept(state, ctx);

		inOrder.verify(ctx).update(state);
		inOrder.verify(ctx).setRecordsInitialHash(EMPTY_HASH);
		inOrder.verify(ctx).rebuildBackingStoresIfPresent();
		inOrder.verify(ctx).rebuildStoreViewsIfPresent();
		inOrder.verify(uniqTokenViewsManager).rebuildNotice(tokens, uniqueTokens);
		inOrder.verify(expiryManager).reviewExistingPayerRecords();
		inOrder.verify(expiryManager).reviewExistingShortLivedEntities();
		inOrder.verify(networkCtxManager).setObservableFilesNotLoaded();
		inOrder.verify(networkCtxManager, never()).loadObservableSysFilesIfNeeded();
	}
}
