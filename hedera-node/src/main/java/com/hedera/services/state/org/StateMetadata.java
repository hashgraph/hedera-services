package com.hedera.services.state.org;

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

import com.hedera.services.ServicesApp;
import com.hedera.services.utils.PermHashInteger;
import com.swirlds.common.FastCopyable;
import com.swirlds.common.merkle.Archivable;
import com.swirlds.fchashmap.FCOneToManyRelation;

/**
 * Contains the part of the Hedera Services world state that does influence
 * handling of consensus transactions, but is not hashed or serialized.
 */
public class StateMetadata implements FastCopyable, Archivable {
	private final ServicesApp app;

	private FCOneToManyRelation<PermHashInteger, Long> uniqueTokenAssociations;
	private FCOneToManyRelation<PermHashInteger, Long> uniqueOwnershipAssociations;
	private FCOneToManyRelation<PermHashInteger, Long> uniqueTreasuryOwnershipAssociations;

	public StateMetadata(ServicesApp app) {
		this.app = app;
		this.uniqueTokenAssociations = new FCOneToManyRelation<>();
		this.uniqueOwnershipAssociations = new FCOneToManyRelation<>();
		this.uniqueTreasuryOwnershipAssociations = new FCOneToManyRelation<>();
	}

	private StateMetadata(StateMetadata that) {
		this.uniqueTokenAssociations = that.uniqueTokenAssociations.copy();
		this.uniqueOwnershipAssociations = that.uniqueOwnershipAssociations.copy();
		this.uniqueTreasuryOwnershipAssociations = that.uniqueTreasuryOwnershipAssociations.copy();
		this.app = that.app;
	}

	@Override
	public void archive() {
		release();
	}

	@Override
	public StateMetadata copy() {
		return new StateMetadata(this);
	}

	@Override
	public void release() {
		uniqueTokenAssociations.release();
		uniqueOwnershipAssociations.release();
		uniqueTreasuryOwnershipAssociations.release();
	}

	public ServicesApp app() {
		return app;
	}

	public FCOneToManyRelation<PermHashInteger, Long> getUniqueTokenAssociations() {
		return uniqueTokenAssociations;
	}

	public FCOneToManyRelation<PermHashInteger, Long> getUniqueOwnershipAssociations() {
		return uniqueOwnershipAssociations;
	}

	public FCOneToManyRelation<PermHashInteger, Long> getUniqueTreasuryOwnershipAssociations() {
		return uniqueTreasuryOwnershipAssociations;
	}

	/* --- Only used by unit tests --- */
	void setUniqueTokenAssociations(FCOneToManyRelation<PermHashInteger, Long> uniqueTokenAssociations) {
		this.uniqueTokenAssociations = uniqueTokenAssociations;
	}

	void setUniqueOwnershipAssociations(FCOneToManyRelation<PermHashInteger, Long> uniqueOwnershipAssociations) {
		this.uniqueOwnershipAssociations = uniqueOwnershipAssociations;
	}

	void setUniqueTreasuryOwnershipAssociations(FCOneToManyRelation<PermHashInteger, Long> uniqueTreasuryOwnershipAssociations) {
		this.uniqueTreasuryOwnershipAssociations = uniqueTreasuryOwnershipAssociations;
	}
}
