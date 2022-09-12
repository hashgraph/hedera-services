/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
 *
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
 */
package com.hedera.services.state.migration;

import com.hedera.services.state.merkle.MerkleUniqueToken;
import com.hedera.services.state.submerkle.EntityId;
import com.hedera.services.state.virtual.UniqueTokenValue;
import javax.annotation.Nullable;

/**
 * Intermediate adapter class for an NFT token.
 *
 * <p>This class is an intermediate adapter class that houses both {@link MerkleUniqueToken} and
 * {@link UniqueTokenValue}. It serves to adapt the selected underlying representation to where it
 * is used.
 */
public class UniqueTokenAdapter {

    private final UniqueTokenValue uniqueTokenValue;
    private final MerkleUniqueToken merkleUniqueToken;

    private final boolean isVirtual;

    @Nullable
    public static UniqueTokenAdapter wrap(@Nullable final MerkleUniqueToken token) {
        return token == null ? null : new UniqueTokenAdapter(token);
    }

    @Nullable
    public static UniqueTokenAdapter wrap(@Nullable final UniqueTokenValue token) {
        return token == null ? null : new UniqueTokenAdapter(token);
    }

    public static UniqueTokenAdapter newEmptyMerkleToken() {
        return wrap(new MerkleUniqueToken());
    }

    UniqueTokenAdapter(final UniqueTokenValue token) {
        merkleUniqueToken = null;

        uniqueTokenValue = token;
        isVirtual = true;
    }

    UniqueTokenAdapter(final MerkleUniqueToken token) {
        uniqueTokenValue = null;

        merkleUniqueToken = token;
        isVirtual = false;
    }

    public boolean isVirtual() {
        return isVirtual;
    }

    public UniqueTokenValue uniqueTokenValue() {
        return uniqueTokenValue;
    }

    public MerkleUniqueToken merkleUniqueToken() {
        return merkleUniqueToken;
    }

    /**
     * Convenience function for accessing underlying owner.
     *
     * @return owner entity id of this token.
     */
    public EntityId getOwner() {
        return isVirtual ? uniqueTokenValue.getOwner() : merkleUniqueToken.getOwner();
    }

    /**
     * Convenience function for accessing underlying spender.
     *
     * @return spender entity id of this token.
     */
    public EntityId getSpender() {
        return isVirtual ? uniqueTokenValue.getSpender() : merkleUniqueToken.getSpender();
    }

    /**
     * Convenience function for accessing underlying immutability state.
     *
     * @return whether the underlying instance is immutable.
     */
    public boolean isImmutable() {
        return isVirtual ? uniqueTokenValue.isImmutable() : merkleUniqueToken.isImmutable();
    }

    /**
     * Convenience function for accessing underlying packed creation time value.
     *
     * @return packed creation time of underlying instance.
     */
    public long getPackedCreationTime() {
        return isVirtual
                ? uniqueTokenValue.getPackedCreationTime()
                : merkleUniqueToken.getPackedCreationTime();
    }

    /**
     * Convenience function for accessing underlying instance's metadata.
     *
     * @return byte array containing the metadata of the underlying instance.
     */
    public byte[] getMetadata() {
        return isVirtual ? uniqueTokenValue.getMetadata() : merkleUniqueToken.getMetadata();
    }

    /**
     * Convenience function updating underlying spender.
     *
     * @param spender EntityId of the spender.
     */
    public void setSpender(final EntityId spender) {
        if (isVirtual) {
            uniqueTokenValue.setSpender(spender);
        } else {
            merkleUniqueToken.setSpender(spender);
        }
    }

    /**
     * Convenience function updating underlying owner.
     *
     * @param owner EntityId of the owner.
     */
    public void setOwner(final EntityId owner) {
        if (isVirtual) {
            uniqueTokenValue.setOwner(owner);
        } else {
            merkleUniqueToken.setOwner(owner);
        }
    }

    /**
     * Convenience function for updating underlying packed creation timestamp.
     *
     * @param packedCreationTime long representing the pack creation timestamp.
     */
    public void setPackedCreationTime(final long packedCreationTime) {
        if (isVirtual) {
            uniqueTokenValue.setPackedCreationTime(packedCreationTime);
        } else {
            merkleUniqueToken.setPackedCreationTime(packedCreationTime);
        }
    }

    /**
     * Convenience function for updating underlying metadata.
     *
     * @param metadata byte array representing the updated metadata to set the instance's metadata
     *     to.
     */
    public void setMetadata(final byte[] metadata) {
        if (isVirtual) {
            uniqueTokenValue.setMetadata(metadata);
        } else {
            merkleUniqueToken.setMetadata(metadata);
        }
    }

    @Override
    public boolean equals(final Object other) {
        if (other == null || other.getClass() != UniqueTokenAdapter.class) {
            return false;
        }
        if (other == this) {
            return true;
        }
        return isVirtual
                ? uniqueTokenValue.equals(((UniqueTokenAdapter) other).uniqueTokenValue)
                : merkleUniqueToken.equals(((UniqueTokenAdapter) other).merkleUniqueToken);
    }

    @Override
    public int hashCode() {
        return isVirtual ? uniqueTokenValue.hashCode() : merkleUniqueToken.hashCode();
    }
}
