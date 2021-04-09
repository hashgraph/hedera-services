package com.hedera.services.bdd.suites.utils.sysfiles;

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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.common.primitives.Ints;
import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiPropertySource;
import com.hedera.services.legacy.proto.utils.CommonUtils;
import com.hederahashgraph.api.proto.java.NodeAddress;
import com.hederahashgraph.api.proto.java.ServiceEndpoint;
import org.apache.commons.codec.binary.Hex;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class BookEntryPojo {
	public static String CRTS_DIR = "certs";
	public static String RSA_PUBKEYS_DIR = "pubkeys";

	private String ip;
	private String memo;
	private String nodeAccount;
	private String rsaPubKey;
	private String certHash;
	private Integer port;
	private Long nodeId;
	private List<String> endPoints;

	private List<String> ips = null;

	public static BookEntryPojo fromAddressBookEntry(NodeAddress address) {
		var pojo = fromAnyEntry(address);
		if (address.getServiceEndpointCount() == 0) {
			pojo.endPoints = new ArrayList<>();
		} else {
			pojo.endPoints = address.getServiceEndpointList()
					.stream()
					.map(s -> s.getIpAddressV4() + " : " + s.getPort())
					.collect(Collectors.toList());
		}
		return pojo;
	}

	public static BookEntryPojo fromNodeDetailsEntry(NodeAddress address) {
		var pojo = fromAnyEntry(address);
		pojo.rsaPubKey = address.getRSAPubKey();
		if (pojo.rsaPubKey.length() == 0) {
			pojo.rsaPubKey = null;
		}
		return pojo;
	}

	public Stream<NodeAddress> toAddressBookEntries() {
		var address = NodeAddress.newBuilder();

		address.setIpAddress(ByteString.copyFromUtf8(ip));
		address.setPortno(Optional.ofNullable(port).orElse(0));

		addBasicBioTo(address);
		buildEndPointsIfExist(address);

		if (certHash != null) {
			if ("!".equals(certHash)) {
				certHash = asHexEncodedSha384HashFor(address.getNodeId());
			}
			address.setNodeCertHash(ByteString.copyFrom(certHash.getBytes()));
		}

		return Stream.of(address.build());
	}

	static String asHexEncodedSha384HashFor(long nodeId) {
		try {
			var crtBytes = Files.readAllBytes(Paths.get(CRTS_DIR, String.format("node%d.crt", nodeId)));
			var crtHash = CommonUtils.noThrowSha384HashOf(crtBytes);
			return Hex.encodeHexString(crtHash);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	public Stream<NodeAddress> toNodeDetailsEntry() {
		var address = NodeAddress.newBuilder();
		addBasicBioTo(address);
		buildEndPointsIfExist(address);
		address.setIpAddress(ByteString.copyFrom(ip.getBytes()));
		address.setPortno(Optional.ofNullable(port).orElse(0));
		if (rsaPubKey != null) {
			if ("!".equals(rsaPubKey)) {
				rsaPubKey = asHexEncodedDerPubKey(nodeId);
			}
			address.setRSAPubKey(rsaPubKey);
		}
		return Stream.of(address.build());
	}

	public static String asHexEncodedDerPubKey(long nodeId) {
		try {
			var pubKeyBytes = Files.readAllBytes(Paths.get(RSA_PUBKEYS_DIR, String.format("node%d.der", nodeId)));
			return Hex.encodeHexString(pubKeyBytes);
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private void addBasicBioTo(NodeAddress.Builder builder) {
		builder.setNodeId(nodeId);
		builder.setMemo(ByteString.copyFrom(memo.getBytes()));
		if (nodeAccount != null) {
			builder.setNodeAccountId(HapiPropertySource.asAccount(nodeAccount));
		}
	}

	private void buildEndPointsIfExist(NodeAddress.Builder builder) {
		if (endPoints == null) {
			return;
		}
		for (String endPoint : endPoints) {
			var serviceEndpoint = ServiceEndpoint.newBuilder();
			String[] elements = endPoint.split(":");
			serviceEndpoint.setIpAddressV4(ByteString.copyFrom(ipv4Bytes(elements[0].trim())));
			serviceEndpoint.setPort(Integer.parseInt(elements[1].trim()));
			builder.addServiceEndpoint(serviceEndpoint.build());
		}
	}

	private static byte[] ipv4Bytes(String dotDelimited) {
		try {
			var bytes = new byte[4];
			var parts = dotDelimited.split("[.]");
			for (int i = 0; i < 4; i++) {
				System.arraycopy(Ints.toByteArray(Integer.parseInt(parts[i])), 0, bytes, i * 4, 4);
			}
			return bytes;
		} catch (Exception e) {
			throw new IllegalArgumentException(dotDelimited + " is not an IPv4 address!");
		}
	}

	private static BookEntryPojo fromAnyEntry(NodeAddress address) {
		var entry = new BookEntryPojo();

		if (address.getMemo().isEmpty()) {
			entry.memo = "<N/A>";
		} else {
			entry.memo = new String(address.getMemo().toByteArray());
		}

		entry.nodeId = address.getNodeId();
		if (address.hasNodeAccountId()) {
			entry.nodeAccount = HapiPropertySource.asAccountString(address.getNodeAccountId());
		} else {
			entry.nodeAccount = "<N/A>";
		}

		entry.ip = new String(address.getIpAddress().toByteArray());
		entry.port = address.getPortno();
		if (entry.port == 0) {
			entry.port = null;
		}
		if (address.getNodeCertHash().isEmpty()) {
			entry.certHash = "<N/A>";
		} else {
			entry.certHash = new String(address.getNodeCertHash().toByteArray());
		}
		return entry;
	}

	public String getIp() {
		return ip;
	}

	public void setIp(String ip) {
		this.ip = ip;
	}

	public String getMemo() {
		return memo;
	}

	public void setMemo(String memo) {
		this.memo = memo;
	}

	public String getNodeAccount() {
		return nodeAccount;
	}

	public void setNodeAccount(String nodeAccount) {
		this.nodeAccount = nodeAccount;
	}

	public String getRsaPubKey() {
		return rsaPubKey;
	}

	public void setRsaPubKey(String rsaPubKey) {
		this.rsaPubKey = rsaPubKey;
	}

	public String getCertHash() {
		return certHash;
	}

	public void setCertHash(String certHash) {
		this.certHash = certHash;
	}

	public Integer getPort() {
		return port;
	}

	public void setPort(Integer port) {
		this.port = port;
	}

	public Long getNodeId() {
		return nodeId;
	}

	public void setNodeId(Long nodeId) {
		this.nodeId = nodeId;
	}

	public List<String> getIps() {
		return ips;
	}

	public void setIps(List<String> ips) {
		this.ips = ips;
	}

	public List<String> getEndPoints() {
		return endPoints;
	}

	public void setEndPoints(List<String> endPoints) {
		this.endPoints = endPoints;
	}
}
