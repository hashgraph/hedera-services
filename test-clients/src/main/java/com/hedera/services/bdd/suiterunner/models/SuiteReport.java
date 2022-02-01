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

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

import java.util.List;

public class SuiteReport {
	private final String name;
	private final String status;
	private List<SpecReport> specReports;

	public SuiteReport(final String name, final String status) {
		this.name = name;
		this.status = status;
	}

	public String getStatus() {
		return status;
	}

	public String getName() {
		return name;
	}

	public List<SpecReport> getFailedSpecs() {
		return specReports;
	}

	public void setFailedSpecs(final List<SpecReport> specReports) {
		this.specReports = specReports;
	}

	@Override
	public boolean equals(final Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		final SuiteReport that = (SuiteReport) o;
		return new EqualsBuilder().append(getName(), that.getName()).isEquals();
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder(17, 37).append(getName()).toHashCode();
	}
}


