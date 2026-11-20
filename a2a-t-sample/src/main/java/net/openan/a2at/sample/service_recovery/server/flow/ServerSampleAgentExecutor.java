/*
 * Copyright (c) 2026 Huawei Technologies Co., Ltd.
 * All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 *    Licensed under the Apache License, Version 2.0 (the "License"); you may
 *    not use this file except in compliance with the License. You may obtain
 *    a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 *    WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 *    License for the specific language governing permissions and limitations
 *    under the License.
 */

package net.openan.a2at.sample.service_recovery.server.flow;

import java.util.Map;
import java.util.function.Consumer;
import org.a2aproject.sdk.server.agentexecution.AgentExecutor;
import org.a2aproject.sdk.server.agentexecution.RequestContext;
import org.a2aproject.sdk.server.tasks.AgentEmitter;
import org.a2aproject.sdk.spec.A2AError;

/**
 * Real a2a-java agent executor for the service-recovery server flow.
 *
 * @since 2026-08
 */
public final class ServerSampleAgentExecutor implements AgentExecutor {
    private final NotificationPromptValidator promptValidator;

    private final ServerSleepController sleepController;

    private final Map<String, Object> recoveryEventArtifactData;

    private final Consumer<String> logSink;

    /**
     * Creates the agent executor.
     *
     * @param promptValidator notification prompt validator backed by the server SDK
     * @param sleepController controllable sleep used by the recurring artifact loop
     * @param recoveryEventArtifactData mock service recovery event payload emitted as artifacts
     * @param logSink log sink, may be null
     */
    public ServerSampleAgentExecutor(
            NotificationPromptValidator promptValidator,
            ServerSleepController sleepController,
            Map<String, Object> recoveryEventArtifactData,
            Consumer<String> logSink) {
        this.promptValidator = promptValidator;
        this.sleepController = sleepController;
        this.recoveryEventArtifactData = recoveryEventArtifactData;
        this.logSink = logSink;
    }

    @Override
    public void execute(RequestContext requestContext, AgentEmitter agentEmitter) throws A2AError {
        ServerSampleFlow.executeServerFlow(
                requestContext, promptValidator, agentEmitter, sleepController, recoveryEventArtifactData, logSink);
    }

    @Override
    public void cancel(RequestContext requestContext, AgentEmitter agentEmitter) throws A2AError {
        agentEmitter.cancel();
    }
}
