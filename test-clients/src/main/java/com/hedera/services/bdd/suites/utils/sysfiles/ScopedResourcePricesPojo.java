package com.hedera.services.bdd.suites.utils.sysfiles;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScopedResourcePricesPojo {
	ResourcePricesPojo nodedata, networkdata, servicedata;

	public ResourcePricesPojo getNodedata() {
		return nodedata;
	}

	public void setNodedata(ResourcePricesPojo nodedata) {
		this.nodedata = nodedata;
	}

	public ResourcePricesPojo getNetworkdata() {
		return networkdata;
	}

	public void setNetworkdata(ResourcePricesPojo networkdata) {
		this.networkdata = networkdata;
	}

	public ResourcePricesPojo getServicedata() {
		return servicedata;
	}

	public void setServicedata(ResourcePricesPojo servicedata) {
		this.servicedata = servicedata;
	}
}
