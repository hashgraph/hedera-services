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
package com.hedera.node.app.keys.impl;

import com.hedera.node.app.spi.keys.HederaKey;
import com.hedera.node.app.spi.keys.HederaReplKey;
import com.hedera.services.state.virtual.annotations.StateSetter;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

/** A HederaKey that is a list of HederaKeys. */
public class HederaKeyList extends AbstractHederaKey {
    private static final long CLASS_ID = 15512048L;
    private static final int VERSION = 1;
    private List<HederaReplKey> keys;
    private boolean immutable = false;

    public HederaKeyList() {
        this.keys = new LinkedList<>();
    }

    public HederaKeyList(@Nonnull List<HederaReplKey> keys) {
        Objects.requireNonNull(keys);
        this.keys = keys;
    }

    public HederaKeyList(@Nonnull HederaKeyList that) {
        Objects.requireNonNull(that);
        this.keys = that.keys;
    }

    @Override
    public boolean isPrimitive() {
        return false;
    }

    @Override
    public boolean isEmpty() {
        if (keys != null) {
            for (var key : keys) {
                if ((null != key) && !key.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public boolean isValid() {
        if (isEmpty()) {
            return false;
        }
        for (var key : keys) {
            if ((null == key) || !key.isValid()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public HederaKeyList copy() {
        this.immutable = true;
        return new HederaKeyList(this);
    }

    @Override
    public HederaKeyList asReadOnly() {
        final var copy = new HederaKeyList(this);
        copy.immutable = true;
        return copy;
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        final var len = keys.size();
        out.writeInt(len);
        for (final var key : keys) {
            out.writeSerializable(key, true);
        }
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version)
            throws IOException {
        final var len = in.readInt();
        for (int i = 0; i < len; i++) {
            final HederaReplKey childKey = in.readSerializable();
            keys.add(childKey);
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (o == this) {
            return true;
        }
        if (o == null || HederaKeyList.class != o.getClass()) {
            return false;
        }
        final var that = (HederaKeyList) o;
        return new EqualsBuilder().append(keys, that.keys).isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder().append(keys).build();
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("keys", keys)
                .toString();
    }

    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    @Override
    public int getVersion() {
        return VERSION;
    }

    @Override
    public boolean isImmutable() {
        return immutable;
    }

    @Override
    public void visitPrimitiveKeys(final Consumer<HederaKey> actionOnSimpleKey) {
        keys.forEach(k -> k.visitPrimitiveKeys(actionOnSimpleKey));
    }

    /* ------- Object's setters and getters --------- */

    public List<HederaReplKey> getKeys() {
        return keys;
    }

    @StateSetter
    public void setKeys(final List<HederaReplKey> keys) {
        throwIfImmutable("Tried to set the keys on an immutable HederaKeyList");
        this.keys = keys;
    }
}
