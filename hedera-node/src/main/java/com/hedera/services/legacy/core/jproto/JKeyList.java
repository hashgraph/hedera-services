/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.legacy.core.jproto;

import java.util.LinkedList;
import java.util.List;

/** Maps to proto Key of type KeyList. */
public class JKeyList extends JKey {
    private List<JKey> keys;

    public JKeyList() {
        this.keys = new LinkedList<>();
    }

    public JKeyList(List<JKey> keys) {
        if (keys == null) {
            throw new IllegalArgumentException(
                    "JKeyList cannot be constructed with a null 'keys' argument!");
        }
        this.keys = keys;
    }

    @Override
    public String toString() {
        return "<JKeyList: keys=" + keys.toString() + ">";
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
        } else {
            for (var key : keys) {
                // if any key is null or invalid then this key list is invalid
                if ((null == key) || !key.isValid()) {
                    return false;
                }
            }
            return true;
        }
    }

    @Override
    public boolean hasKeyList() {
        return true;
    }

    public List<JKey> getKeysList() {
        return keys;
    }

    @Override
    public JKeyList getKeyList() {
        return this;
    }

    @Override
    public void setForScheduledTxn(boolean flag) {
        if (keys != null) {
            for (JKey key : keys) {
                key.setForScheduledTxn(flag);
            }
        }
    }

    @Override
    public boolean isForScheduledTxn() {
        if (keys != null) {
            for (JKey key : keys) {
                if (key.isForScheduledTxn()) {
                    return true;
                }
            }
        }
        return false;
    }
}
