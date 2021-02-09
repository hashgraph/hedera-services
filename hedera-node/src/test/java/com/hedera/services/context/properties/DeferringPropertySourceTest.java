package com.hedera.services.context.properties;

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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.*;

class DeferringPropertySourceTest {
	String name = "something";
	Object value = new Object();

	PropertySource properties;
	Map<String, Object> defaults;
	DeferringPropertySource subject;

	@BeforeEach
	private void setup() {
		defaults = mock(Map.class);
		properties = mock(PropertySource.class);

		subject = new DeferringPropertySource(properties, defaults);
	}

	@Test
	public void prioritizesContains() {
		given(properties.containsProperty(name)).willReturn(true);

		// when:
		var flag = subject.containsProperty(name);

		// then:
		assertTrue(flag);
		verify(properties).containsProperty(name);
		verify(defaults, never()).containsKey(name);
	}

	@Test
	public void fallsBackForContains() {
		given(properties.containsProperty(name)).willReturn(false);
		given(defaults.containsKey(name)).willReturn(true);

		// when:
		var flag = subject.containsProperty(name);

		// then:
		assertTrue(flag);
		verify(properties).containsProperty(name);
		verify(defaults).containsKey(name);
	}

	@Test
	public void prioritizesGet() {
		given(properties.containsProperty(name)).willReturn(true);
		given(properties.getProperty(name)).willReturn(value);

		// when:
		var actual = subject.getProperty(name);

		// then:
		assertEquals(value, actual);
		verify(properties).getProperty(name);
		verify(defaults, never()).get(name);
	}

	@Test
	public void fallsBackForGet() {
		given(properties.containsProperty(name)).willReturn(false);
		given(defaults.get(name)).willReturn(value);

		// when:
		var actual = subject.getProperty(name);

		// then:
		assertEquals(value, actual);
		verify(properties).containsProperty(name);
		verify(properties, never()).getProperty(name);
		verify(defaults).get(name);
	}

	@Test
	void unionOfNamesReturned() {
		given(defaults.keySet()).willReturn(Set.of("a", "b"));
		given(properties.allPropertyNames()).willReturn(Set.of("b", "c"));

		// when:
		var keys = subject.allPropertyNames();

		// then:
		assertEquals(Set.of("a", "b", "c"), keys);
	}
}
