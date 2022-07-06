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
package com.hedera.services.state.submerkle;

import com.google.common.base.MoreObjects;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * Represents the value for {@code nftAllowances} map in {@code MerkleAccountState}. It consists of
 * the information about the list of serial numbers of a non-fungible token for which allowance is
 * granted to the spender. If {@code approvedForAll} is true, spender has been granted access to all
 * instances of the non-fungible token.
 *
 * <p>Having allowance on a token will allow the spender to transfer granted token units from the
 * owner's account.
 */
public class FcTokenAllowance implements SelfSerializable {
    static final int RELEASE_023X_VERSION = 1;
    static final int CURRENT_VERSION = RELEASE_023X_VERSION;
    static final long RUNTIME_CONSTRUCTABLE_ID = 0xf65baa533950f139L;

    private boolean approvedForAll;
    private List<Long> serialNumbers;

    public FcTokenAllowance() {
        /* RuntimeConstructable */
    }

    FcTokenAllowance(final boolean approvedForAll, final List<Long> serialNumbers) {
        this.approvedForAll = approvedForAll;
        this.serialNumbers = serialNumbers;
    }

    FcTokenAllowance(final boolean approvedForAll) {
        this.approvedForAll = approvedForAll;
        this.serialNumbers = Collections.emptyList();
    }

    FcTokenAllowance(final List<Long> serialNumbers) {
        this.serialNumbers = serialNumbers;
        this.approvedForAll = false;
    }

    @Override
    public void deserialize(final SerializableDataInputStream din, final int i) throws IOException {
        approvedForAll = din.readBoolean();
        serialNumbers = din.readLongList(Integer.MAX_VALUE);
    }

    @Override
    public void serialize(final SerializableDataOutputStream dos) throws IOException {
        dos.writeBoolean(approvedForAll);
        dos.writeLongList(serialNumbers);
    }

    @Override
    public long getClassId() {
        return RUNTIME_CONSTRUCTABLE_ID;
    }

    @Override
    public int getVersion() {
        return CURRENT_VERSION;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || !obj.getClass().equals(FcTokenAllowance.class)) {
            return false;
        }

        final var that = (FcTokenAllowance) obj;
        return new EqualsBuilder()
                .append(approvedForAll, that.approvedForAll)
                .append(serialNumbers, that.serialNumbers)
                .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(approvedForAll).append(serialNumbers).toHashCode();
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .omitNullValues()
                .add("approvedForAll", approvedForAll)
                .add("serialNumbers", serialNumbers)
                .toString();
    }

    public boolean isApprovedForAll() {
        return approvedForAll;
    }

    public List<Long> getSerialNumbers() {
        return serialNumbers;
    }

    public static FcTokenAllowance from(
            final boolean approvedForAll, final List<Long> serialNumbers) {
        final var modifiableList = new ArrayList<>(serialNumbers);
        Collections.sort(modifiableList);
        return new FcTokenAllowance(approvedForAll, modifiableList);
    }

    public static FcTokenAllowance from(final boolean approvedForAll) {
        return new FcTokenAllowance(approvedForAll);
    }

    public static FcTokenAllowance from(final List<Long> serialNumbers) {
        final var modifiableList = new ArrayList<>(serialNumbers);

        Collections.sort(modifiableList);
        return new FcTokenAllowance(modifiableList);
    }
}
