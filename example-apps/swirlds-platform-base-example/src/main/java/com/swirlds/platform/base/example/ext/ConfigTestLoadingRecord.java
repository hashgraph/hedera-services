/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.base.example.ext;

import com.google.common.base.Preconditions;
import com.swirlds.config.api.ConfigData;
import com.swirlds.config.api.ConfigProperty;
import com.swirlds.config.api.DefaultValue;
import com.swirlds.config.api.EmptyValue;
import com.swirlds.config.api.UnsetValue;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ConfigData("base.property.test")
public record ConfigTestLoadingRecord(
        @ConfigProperty("class") @DefaultValue("") String value,
        @DefaultValue("value2") String value2,
        @UnsetValue boolean value3,
        @UnsetValue int valueA,
        @UnsetValue char valueB,
        @UnsetValue double valueC,
        @UnsetValue float valueD,
        @UnsetValue String valueE,
        @UnsetValue List<String> valueF,
        @UnsetValue Set<String> valueG,
        @UnsetValue List<String> value4,
        @UnsetValue Set<String> value5,
        @DefaultValue("[]") List<String> value6,
        @DefaultValue("") List<String> value7,
        @EmptyValue List<String> value8,
        @EmptyValue String value9,
        @DefaultValue("Value,Value1,Value2,Value3") List<String> value10) {

    public void check() {
        Preconditions.checkArgument(this.value().equals("THIS VALUE WAS REPLACED"));
        Preconditions.checkArgument(this.value2().equals("value2"));
        Preconditions.checkArgument(!this.value3());
        Preconditions.checkArgument(this.value4() == null);
        Preconditions.checkArgument(this.value5() == null);
        Preconditions.checkArgument(this.value6().isEmpty());
        Preconditions.checkArgument(this.value7().isEmpty());
        Preconditions.checkArgument(this.value8().isEmpty());
        Preconditions.checkArgument(this.value9().isEmpty());
        Preconditions.checkArgument(new HashSet<>(this.value10()).containsAll(List.of("Value", "Value1", "Value2")));
        Preconditions.checkArgument(this.valueA() == 0);
        Preconditions.checkArgument(this.valueB() == 0);
        Preconditions.checkArgument(this.valueC() == 0.0);
        Preconditions.checkArgument(this.valueD() == 0.0);
        Preconditions.checkArgument(this.valueE() == null);
        Preconditions.checkArgument(this.valueF() == null);
        Preconditions.checkArgument(this.valueG() == null);
    }
}
