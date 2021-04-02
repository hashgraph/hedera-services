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

public class NodeLocalProperties {
	private final PropertySource properties;

	private int port;
	private int tlsPort;
	private int precheckLookupRetries;
	private int precheckLookupRetryBackoffMs;
	private long statsHapiOpsSpeedometerUpdateIntervalMs;
	private Profile activeProfile;
	private double statsSpeedometerHalfLifeSecs;
	private double statsRunningAvgHalfLifeSecs;
	private String recordLogDir;
	private long recordLogPeriod;
	private boolean recordStreamEnabled;
	private int recordStreamQueueCapacity;
	private int queryBlobLookupRetries;
	private long nettyProdKeepAliveTime;
	private String nettyTlsCrtPath;
	private String nettyTlsKeyPath;
	private long nettyProdKeepAliveTimeout;
	private long nettyMaxConnectionAge;
	private long nettyMaxConnectionAgeGrace;
	private long nettyMaxConnectionIdle;
	private int nettyMaxConcurrentCalls;
	private int nettyFlowControlWindow;
	private String devListeningAccount;
	private boolean devOnlyDefaultNodeListens;
	private String accountsExportPath;

	public NodeLocalProperties(PropertySource properties) {
		this.properties = properties;

		reload();
	}

	public void reload() {
		port = properties.getIntProperty("grpc.port");
		tlsPort = properties.getIntProperty("grpc.tlsPort");
		precheckLookupRetries = properties.getIntProperty("precheck.account.maxLookupRetries");
		precheckLookupRetryBackoffMs = properties.getIntProperty("precheck.account.lookupRetryBackoffIncrementMs");
		activeProfile = properties.getProfileProperty("hedera.profiles.active");
		statsHapiOpsSpeedometerUpdateIntervalMs = properties.getLongProperty("stats.hapiOps.speedometerUpdateIntervalMs");
		statsSpeedometerHalfLifeSecs = properties.getDoubleProperty("stats.speedometerHalfLifeSecs");
		statsRunningAvgHalfLifeSecs = properties.getDoubleProperty("stats.runningAvgHalfLifeSecs");
		recordLogDir = properties.getStringProperty("hedera.recordStream.logDir");
		recordLogPeriod = properties.getLongProperty("hedera.recordStream.logPeriod");
		recordStreamEnabled = properties.getBooleanProperty("hedera.recordStream.isEnabled");
		recordStreamQueueCapacity = properties.getIntProperty("hedera.recordStream.queueCapacity");
		queryBlobLookupRetries = properties.getIntProperty("queries.blob.lookupRetries");
		nettyProdKeepAliveTime = properties.getLongProperty("netty.prod.keepAliveTime");
		nettyTlsCrtPath = properties.getStringProperty("netty.tlsCrt.path");
		nettyTlsKeyPath = properties.getStringProperty("netty.tlsKey.path");
		nettyProdKeepAliveTimeout = properties.getLongProperty("netty.prod.keepAliveTimeout");
		nettyMaxConnectionAge = properties.getLongProperty("netty.prod.maxConnectionAge");
		nettyMaxConnectionAgeGrace = properties.getLongProperty("netty.prod.maxConnectionAgeGrace");
		nettyMaxConnectionIdle = properties.getLongProperty("netty.prod.maxConnectionIdle");
		nettyMaxConcurrentCalls = properties.getIntProperty("netty.prod.maxConcurrentCalls");
		nettyFlowControlWindow = properties.getIntProperty("netty.prod.flowControlWindow");
		devListeningAccount = properties.getStringProperty("dev.defaultListeningNodeAccount");
		devOnlyDefaultNodeListens = properties.getBooleanProperty("dev.onlyDefaultNodeListens");
		accountsExportPath = properties.getStringProperty("hedera.accountsExportPath");
	}

	public int port() {
		return port;
	}

	public int tlsPort() {
		return tlsPort;
	}

	public int precheckLookupRetries() {
		return precheckLookupRetries;
	}

	public int precheckLookupRetryBackoffMs() {
		return precheckLookupRetryBackoffMs;
	}

	public Profile activeProfile() {
		return activeProfile;
	}

	public long statsHapiOpsSpeedometerUpdateIntervalMs() {
		return statsHapiOpsSpeedometerUpdateIntervalMs;
	}

	public double statsSpeedometerHalfLifeSecs() {
		return statsSpeedometerHalfLifeSecs;
	}

	public double statsRunningAvgHalfLifeSecs() {
		return statsRunningAvgHalfLifeSecs;
	}

	public String recordLogDir() {
		return recordLogDir;
	}

	public long recordLogPeriod() {
		return recordLogPeriod;
	}

	public boolean isRecordStreamEnabled() {
		return recordStreamEnabled;
	}

	public int recordStreamQueueCapacity() {
		return recordStreamQueueCapacity;
	}

	public int queryBlobLookupRetries() {
		return queryBlobLookupRetries;
	}

	public long nettyProdKeepAliveTime() {
		return nettyProdKeepAliveTime;
	}

	public String nettyTlsCrtPath() {
		return nettyTlsCrtPath;
	}

	public String nettyTlsKeyPath() {
		return nettyTlsKeyPath;
	}

	public long nettyProdKeepAliveTimeout() {
		return nettyProdKeepAliveTimeout;
	}

	public long nettyMaxConnectionAge() {
		return nettyMaxConnectionAge;
	}

	public long nettyMaxConnectionAgeGrace() {
		return nettyMaxConnectionAgeGrace;
	}

	public long nettyMaxConnectionIdle() {
		return nettyMaxConnectionIdle;
	}

	public int nettyMaxConcurrentCalls() {
		return nettyMaxConcurrentCalls;
	}

	public int nettyFlowControlWindow() {
		return nettyFlowControlWindow;
	}

	public String devListeningAccount() {
		return devListeningAccount;
	}

	public boolean devOnlyDefaultNodeListens() {
		return devOnlyDefaultNodeListens;
	}

	public String accountsExportPath() {
		return accountsExportPath;
	}
}
