/*
 * Copyright 2016-2023 Hedera Hashgraph, LLC
 *
 * This software is the confidential and proprietary information of
 * Hedera Hashgraph, LLC. ("Confidential Information"). You shall not
 * disclose such Confidential Information and shall use it only in
 * accordance with the terms of the license agreement you entered into
 * with Hedera Hashgraph.
 *
 * HEDERA HASHGRAPH MAKES NO REPRESENTATIONS OR WARRANTIES ABOUT THE SUITABILITY OF
 * THE SOFTWARE, EITHER EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED
 * TO THE IMPLIED WARRANTIES OF MERCHANTABILITY, FITNESS FOR A
 * PARTICULAR PURPOSE, OR NON-INFRINGEMENT. HEDERA HASHGRAPH SHALL NOT BE LIABLE FOR
 * ANY DAMAGES SUFFERED BY LICENSEE AS A RESULT OF USING, MODIFYING OR
 * DISTRIBUTING THIS SOFTWARE OR ITS DERIVATIVES.
 */

package com.swirlds.platform.event.tipset;

import com.swirlds.common.crypto.Hash;

/**
 * Uniquely identifies an event and stores basic metadata bout it.
 *
 * @param creator
 * 		the ID of the node that created this event
 * @param generation
 * 		the generation of the event
 * @param hash
 * 		the hash of the event, expected to be unique for all events
 */
public record EventFingerprint(long creator, long generation, Hash hash) {

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return hash.hashCode();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(final Object obj) {
		if (this == obj) {
			return true;
		}

		if (obj == null || getClass() != obj.getClass()) {
			return false;
		}

		final EventFingerprint that = (EventFingerprint) obj;
		return this.hash.equals(that.hash);
	}
}
