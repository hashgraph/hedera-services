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

/** Maps to proto Key of type ThresholdKey. */
public class JThresholdKey extends JKey {
    int threshold;
    private JKeyList keys;

    public JThresholdKey(JKeyList keys, int threshold) {
        this.keys = keys;
        this.threshold = threshold;
    }

    @Override
    public String toString() {
        return "<JThresholdKey: thd=" + threshold + ", keys=" + keys.toString() + ">";
    }

    @Override
    public boolean isEmpty() {
        return keys == null || keys.isEmpty();
    }

    @Override
    public boolean hasThresholdKey() {
        return true;
    }

    @Override
    public JThresholdKey getThresholdKey() {
        return this;
    }

    public JKeyList getKeys() {
        return keys;
    }

    public int getThreshold() {
        return threshold;
    }

    @Override
    public boolean isValid() {
        if (isEmpty()) {
            return false;
        } else {
            int length = keys.getKeysList().size();
            return (threshold >= 1 && threshold <= length && keys.isValid());
        }
    }

    @Override
    public void setForScheduledTxn(boolean flag) {
        if (keys != null) {
            keys.setForScheduledTxn(flag);
        }
    }

    @Override
    public boolean isForScheduledTxn() {
        return keys != null && keys.isForScheduledTxn();
    }
}
