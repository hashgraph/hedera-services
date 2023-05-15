/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.services;

import com.hedera.node.app.service.consensus.impl.ConsensusServiceDaggerModule;
import com.hedera.node.app.service.file.impl.FileServiceDaggerModule;
import com.hedera.node.app.service.networkadmin.impl.NetworkAdminServiceDaggerModule;
import com.hedera.node.app.service.schedule.impl.ScheduleServiceDaggerModule;
import com.hedera.node.app.service.token.impl.TokenServiceDaggerModule;
import com.hedera.node.app.service.util.impl.UtilServiceDaggerModule;
import dagger.Module;

/**
 * Dagger module for all services
 */
@Module(
        includes = {
                ConsensusServiceDaggerModule.class,
                FileServiceDaggerModule.class,
                NetworkAdminServiceDaggerModule.class,
                ScheduleServiceDaggerModule.class,
                TokenServiceDaggerModule.class,
                UtilServiceDaggerModule.class,
        })
public interface ServicesDaggerModule {
}
