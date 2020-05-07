package com.hedera.services;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.HederaNodeContext;
import com.hedera.services.context.PrimitiveContext;
import com.hedera.services.context.domain.topic.Topic;
import com.hedera.services.context.properties.StandardizedPropertySources;
import com.hedera.services.sigs.sourcing.DefaultSigBytesProvider;
import com.hedera.services.utils.JvmSystemExits;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.services.utils.SystemExits;
import com.hedera.services.legacy.core.StorageKey;
import com.hedera.services.legacy.core.StorageValue;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hedera.services.legacy.core.MapKey;
import com.hedera.services.context.domain.haccount.HederaAccount;
import com.hedera.services.legacy.config.PropertiesLoader;
import com.swirlds.common.Address;
import com.swirlds.common.AddressBook;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.NodeId;
import com.swirlds.common.Platform;
import com.swirlds.common.SwirldState;
import com.swirlds.common.Transaction;
import com.swirlds.common.io.FCDataInputStream;
import com.swirlds.common.io.FCDataOutputStream;
import com.swirlds.fcmap.FCMap;

import java.io.IOException;
import java.time.Instant;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;

import static com.hedera.services.context.SingletonContextsManager.CONTEXTS;
import static com.hedera.services.sigs.HederaToPlatformSigOps.expandIn;
import static com.hedera.services.utils.EntityIdUtils.accountParsedFromString;

public class ServicesState implements SwirldState.SwirldState2 {
	private static final Logger log = LogManager.getLogger(ServicesState.class);

	NodeId nodeId;
	PrimitiveContext primitives;
	HederaNodeContext ctx;
	SystemExits systemExits = new JvmSystemExits();

	public ServicesState() {
		/* No-op; init [+ copyFrom] must be called to set a valid state and build the app context. */
	}

	/* Used only to create snapshots for signed state proofs. */
	private ServicesState(NodeId nodeId, PrimitiveContext primitives) {
		this.nodeId = nodeId;
		this.primitives = primitives;
	}

	@Override
	public void init(Platform platform, AddressBook addressBook) {
		nodeId = platform.getSelfId();
		if (CONTEXTS.isInitialized(nodeId.getId())) {
			log.error("Services node {} re-initialized, indicating failure to load saved state. Exiting!", nodeId);
			systemExits.fail(1);
		}
		primitives = new PrimitiveContext(addressBook);
		log.info("Initializing context of Services node {} with platform and address book...", nodeId);
		ctx = new HederaNodeContext(
				nodeId,
				platform,
				new StandardizedPropertySources(PropertiesLoader::getFileExistenceCheck),
				primitives);
		CONTEXTS.store(ctx);
		log.info("...done, context is set for Services node {}!", nodeId);
	}

	@Override
	public synchronized void copyFrom(FCDataInputStream inputStream) throws IOException {
		primitives.copyFrom(inputStream);
		log.info("Restoring context of Services node {} from saved state...", nodeId);
		ctx = new HederaNodeContext(
				nodeId,
				ctx.platform(),
				ctx.propertySources(),
				primitives);
		CONTEXTS.store(ctx);
		log.info("...done, context is restored for Services node {}!", nodeId);
	}

	@Override
	public void copyFromExtra(FCDataInputStream inputStream) throws IOException {
		primitives.copyFromExtra(inputStream);
	}

	@Override
	public synchronized void copyTo(FCDataOutputStream outputStream) throws IOException {
		primitives.copyTo(outputStream);
	}

	@Override
	public void copyToExtra(FCDataOutputStream outputStream) throws IOException {
		primitives.copyToExtra(outputStream);
	}

	@Override
	public synchronized FastCopyable copy() {
		return new ServicesState(nodeId, new PrimitiveContext(primitives));
	}

	@Override
	public synchronized void delete() {
		primitives.getStorage().delete();
	}

	@Override
	public AddressBook getAddressBookCopy() {
		return primitives.getAddressBook().copy();
	}

	@Override
	public synchronized void handleTransaction(
			long submittingMember,
			boolean isConsensus,
			Instant creationTime,
			Instant consensusTime,
			com.swirlds.common.Transaction transaction,
			@Nullable Address toBeCreated
	) {
		if (isConsensus) {
			ctx.logic().incorporateConsensusTxn(transaction, consensusTime, submittingMember);
		}
	}

	@Override
	public void expandSignatures(Transaction platformTxn) {
		try {
			PlatformTxnAccessor accessor = new PlatformTxnAccessor(platformTxn);
			expandIn(accessor, ctx.lookupRetryingKeyOrder(), DefaultSigBytesProvider.DEFAULT_SIG_BYTES);
		} catch (InvalidProtocolBufferException e) {
			log.warn("expandSignatures called with non-gRPC txn!", e);
		}
	}

	@Override
	public void noMoreTransactions() {
		/* No-op. */
	}

	@Override
	public synchronized void copyFrom(SwirldState _state) {
		throw new UnsupportedOperationException();
	}

	/* --- These are only used against a signed state; the consensus state has an active context. --- */
	public AccountID getNodeAccountId() {
		Address address = primitives.getAddressBook().getAddress(nodeId.getId());
		String memo = address.getMemo();
		AccountID account = accountParsedFromString(memo);
		return account;
	}

	public FCMap<MapKey, HederaAccount> getAccountMap() {
		return primitives.getAccounts();
	}

	public FCMap<StorageKey, StorageValue> getStorageMap() {
		return primitives.getStorage();
	}

	public FCMap<MapKey, Topic> getTopicsMap() {
		return primitives.getTopics();
	}
}
