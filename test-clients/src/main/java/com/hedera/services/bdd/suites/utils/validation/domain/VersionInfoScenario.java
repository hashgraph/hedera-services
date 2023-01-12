/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.bdd.suites.utils.validation.domain;

public class VersionInfoScenario {
    private static final String CURRENT_SERVICES_SEMVER = "0.5.8";
    private static final String CURRENT_HAPI_PROTO_SEMVER = "0.5.8";

    String servicesSemVer = CURRENT_SERVICES_SEMVER;
    String hapiProtoSemVer = CURRENT_HAPI_PROTO_SEMVER;

    public String getServicesSemVer() {
        return servicesSemVer;
    }

    public void setServicesSemVer(String servicesSemVer) {
        this.servicesSemVer = servicesSemVer;
    }

    public String getHapiProtoSemVer() {
        return hapiProtoSemVer;
    }

    public void setHapiProtoSemVer(String hapiProtoSemVer) {
        this.hapiProtoSemVer = hapiProtoSemVer;
    }
}
