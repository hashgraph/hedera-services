package com.hedera.services.context.properties;

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

import com.hedera.services.exceptions.UnparseablePropertyException;
import com.hederahashgraph.api.proto.java.AccountID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Defines a source of arbitrary properties keyed by strings. Provides
 * strongly typed accessors for commonly used property types.
 *
 * @author Michael Tinker
 */
public interface PropertySource {
	static final Logger log = LogManager.getLogger(PropertySource.class);
	boolean containsProperty(String name);
	Object getProperty(String name);
	Set<String> allPropertyNames();

	default <T> T getTypedProperty(Class<T> type, String name) {
		return type.cast(getProperty(name));
	}
	default String getStringProperty(String name) {
		return getTypedProperty(String.class, name);
	}
	default boolean getBooleanProperty(String name) {
		return getTypedProperty(Boolean.class, name);
	}
	default int getIntProperty(String name) {
		return getTypedProperty(Integer.class, name);
	}
	default double getDoubleProperty(String name) {
		return getTypedProperty(Double.class, name);
	}
	default long getLongProperty(String name) {
		return getTypedProperty(Long.class, name);
	}
	default Profile getProfileProperty(String name) {
		return getTypedProperty(Profile.class, name);
	}
	default AccountID getAccountProperty(String name) {
		String value = "";
		try {
			value = getStringProperty(name);
			long[] nums = Stream.of(value.split("[.]")).mapToLong(Long::parseLong).toArray();
			return AccountID.newBuilder().setShardNum(nums[0])
										.setRealmNum(nums[1])
										.setAccountNum(nums[2]).build();
		} catch (Exception any) {
			log.info(any.getMessage());
			throw new UnparseablePropertyException(name, value);
		}
	}
}
