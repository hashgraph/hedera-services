/*
 * Copyright (C) 2018-2023 Hedera Hashgraph, LLC
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

package com.swirlds.demo.platform;

public class PayloadProperty {
    private int size = 100;
    private PAYLOAD_TYPE type;

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size;
    }

    public PAYLOAD_TYPE getType() {
        return type;
    }

    public void setType(PAYLOAD_TYPE type) {
        this.type = type;
    }

    public PayloadProperty() {}

    public PayloadProperty(int size, PAYLOAD_TYPE type) {
        super();
        this.size = size;
        this.type = type;
    }
}
