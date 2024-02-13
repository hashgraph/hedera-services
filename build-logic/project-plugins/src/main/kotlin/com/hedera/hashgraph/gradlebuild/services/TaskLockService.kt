package com.hedera.hashgraph.gradlebuild.services

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

abstract class TaskLockService : BuildService<BuildServiceParameters.None>