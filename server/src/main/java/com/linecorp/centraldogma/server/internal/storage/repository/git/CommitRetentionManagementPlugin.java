/*
 * Copyright 2021 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.linecorp.centraldogma.server.internal.storage.repository.git;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cronutils.model.time.ExecutionTime;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableScheduledFuture;
import com.google.common.util.concurrent.ListeningScheduledExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

import com.linecorp.centraldogma.server.CentralDogmaConfig;
import com.linecorp.centraldogma.server.CommitRetentionConfig;
import com.linecorp.centraldogma.server.plugin.Plugin;
import com.linecorp.centraldogma.server.plugin.PluginContext;
import com.linecorp.centraldogma.server.plugin.PluginTarget;
import com.linecorp.centraldogma.server.storage.project.Project;
import com.linecorp.centraldogma.server.storage.project.ProjectManager;
import com.linecorp.centraldogma.server.storage.repository.Repository;

import io.netty.util.concurrent.DefaultThreadFactory;

public final class CommitRetentionManagementPlugin implements Plugin {

    private static final Logger logger = LoggerFactory.getLogger(CommitRetentionManagementPlugin.class);

    @Nullable
    private ListeningScheduledExecutorService worker;

    private volatile boolean stopping;
    @Nullable
    private ListenableScheduledFuture<?> scheduledFuture;

    @Override
    public PluginTarget target() {
        return PluginTarget.ALL_REPLICAS;
    }

    @Override
    public boolean isEnabled(CentralDogmaConfig config) {
        return config.commitRetentionConfig() != null;
    }

    @Override
    public CompletionStage<Void> start(PluginContext context) {
        final CommitRetentionConfig commitRetentionConfig = context.config().commitRetentionConfig();
        assert commitRetentionConfig != null;
        worker = MoreExecutors.listeningDecorator(
                Executors.newSingleThreadScheduledExecutor(
                        new DefaultThreadFactory("commit-retention-worker", true)));
        scheduleRemovingOldCommits(context, commitRetentionConfig);

        return CompletableFuture.completedFuture(null);
    }

    private void scheduleRemovingOldCommits(PluginContext context, CommitRetentionConfig config) {
        if (stopping) {
            return;
        }

        final Duration nextExecution = ExecutionTime.forCron(config.schedule())
                                                    .timeToNextExecution(ZonedDateTime.now());
        final ListeningScheduledExecutorService worker = this.worker;
        assert worker != null;
        scheduledFuture = worker.schedule(() -> removeOldCommits(context, config), nextExecution);

        Futures.addCallback(scheduledFuture, new FutureCallback<Object>() {
            @Override
            public void onSuccess(@Nullable Object result) {}

            @Override
            public void onFailure(Throwable cause) {
                if (!stopping) {
                    logger.warn("Commit retention scheduler stopped due to an unexpected exception:",
                                cause);
                }
            }
        }, worker);
    }

    private void removeOldCommits(PluginContext context, CommitRetentionConfig config) {
        if (stopping) {
            return;
        }

        final ProjectManager pm = context.projectManager();
        for (Project project : pm.list().values()) {
            for (Repository repo : project.repos().list().values()) {
                repo.removeOldCommits(config.minRetentionCommits(), config.minRetentionDays());
            }
        }

        scheduleRemovingOldCommits(context, config);
    }

    @Override
    public CompletionStage<Void> stop(PluginContext context) {
        stopping = true;
        if (scheduledFuture != null) {
            scheduledFuture.cancel(false);
        }

        try {
            if (worker != null && !worker.isTerminated()) {
                logger.info("Stopping the commit retention worker ..");
                boolean interruptLater = false;
                while (!worker.isTerminated()) {
                    worker.shutdownNow();
                    try {
                        worker.awaitTermination(1, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        // Interrupt later.
                        interruptLater = true;
                    }
                }
                logger.info("Stopped the commit retention worker.");

                if (interruptLater) {
                    Thread.currentThread().interrupt();
                }
            }
        } catch (Throwable t) {
            logger.warn("Failed to stop the commit retention worker:", t);
        }
        return CompletableFuture.completedFuture(null);
    }
}