package com.hedera.services.bdd.suiterunner.models;

/*-
 * ‌
 * Hedera Services Test Clients
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

import com.hedera.services.bdd.spec.HapiApiSpec;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

public class SpecReport {
	private final String name;
	private final HapiApiSpec.SpecStatus status;
	private final String failureReason;

	public SpecReport(final String name, final HapiApiSpec.SpecStatus status, final String failureReason) {
		this.name = name;
		this.status = status;
		this.failureReason = failureReason;
	}

	public String getName() {
		return name;
	}

	public HapiApiSpec.SpecStatus getStatus() {
		return status;
	}

	public String getFailureReason() {
		return failureReason;
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		final SpecReport that = (SpecReport) o;
		return new EqualsBuilder().append(getName(), that.getName()).isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder(17, 37).append(getName()).toHashCode();
	}
}


