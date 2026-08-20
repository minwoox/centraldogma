/*
 * Copyright 2026 LY Corporation
 *
 * LY Corporation licenses this file to you under the Apache License,
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
package com.linecorp.centraldogma.server.plugin;

import static java.util.Objects.requireNonNull;

import java.util.concurrent.CompletableFuture;

import com.linecorp.centraldogma.server.command.Command;
import com.linecorp.centraldogma.server.command.CommandExecutor;
import com.linecorp.centraldogma.server.command.CommandExecutorStatusManager;
import com.linecorp.centraldogma.server.command.CreateProjectCommand;
import com.linecorp.centraldogma.server.command.CreateRepositoryCommand;
import com.linecorp.centraldogma.server.command.ExecutionContext;
import com.linecorp.centraldogma.server.command.ForcePushCommand;

/**
 * The {@link CommandExecutor} exposed to a {@link Plugin} via {@link PluginContext#commandExecutor()}. It
 * forwards every operation to the server's {@link CommandExecutor} except that it rejects
 * {@link CreateProjectCommand} and {@link CreateRepositoryCommand} (including ones wrapped in a
 * {@link ForcePushCommand}). Projects and repositories must be created through
 * {@link PluginContext#projectProvisioner()} so that encryption at rest and metadata are set up; executing the
 * raw commands would bypass both.
 */
final class ProvisioningRestrictedCommandExecutor implements CommandExecutor {

    private final CommandExecutor delegate;

    ProvisioningRestrictedCommandExecutor(CommandExecutor delegate) {
        this.delegate = requireNonNull(delegate, "delegate");
    }

    @Override
    public int replicaId() {
        return delegate.replicaId();
    }

    @Override
    public boolean isStarted() {
        return delegate.isStarted();
    }

    @Override
    public CompletableFuture<Void> start() {
        return delegate.start();
    }

    @Override
    public CompletableFuture<Void> stop() {
        return delegate.stop();
    }

    @Override
    public boolean isWritable() {
        return delegate.isWritable();
    }

    @Override
    public void setWritable(boolean writable) {
        delegate.setWritable(writable);
    }

    @Override
    public <T> CompletableFuture<T> execute(ExecutionContext ctx, Command<T> command) {
        rejectIfProvisioningCommand(command);
        return delegate.execute(ctx, command);
    }

    @Override
    public CommandExecutorStatusManager statusManager() {
        return delegate.statusManager();
    }

    private static void rejectIfProvisioningCommand(Command<?> command) {
        Command<?> unwrapped = command;
        if (unwrapped instanceof ForcePushCommand) {
            unwrapped = ((ForcePushCommand<?>) unwrapped).delegate();
        }
        if (unwrapped instanceof CreateProjectCommand || unwrapped instanceof CreateRepositoryCommand) {
            throw new UnsupportedOperationException(
                    "Cannot execute " + command.type() + " directly. Use " +
                    "PluginContext.projectProvisioner() to create projects and repositories so that " +
                    "encryption at rest and metadata are set up.");
        }
    }
}
