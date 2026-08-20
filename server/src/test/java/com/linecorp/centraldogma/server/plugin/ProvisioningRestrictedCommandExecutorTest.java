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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.Test;

import com.linecorp.armeria.common.util.UnmodifiableFuture;
import com.linecorp.centraldogma.common.Author;
import com.linecorp.centraldogma.server.command.Command;
import com.linecorp.centraldogma.server.command.CommandExecutor;
import com.linecorp.centraldogma.server.command.ExecutionContext;

class ProvisioningRestrictedCommandExecutorTest {

    @Test
    void rejectsCreateProjectCommand() {
        final CommandExecutor delegate = mock(CommandExecutor.class);
        final CommandExecutor restricted = new ProvisioningRestrictedCommandExecutor(delegate);

        final Command<Void> command = Command.createProject(Author.SYSTEM, "foo");
        assertThatThrownBy(() -> restricted.execute(command))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("projectProvisioner()");
        verify(delegate, never()).execute(any(), any());
    }

    @Test
    void rejectsCreateRepositoryCommand() {
        final CommandExecutor delegate = mock(CommandExecutor.class);
        final CommandExecutor restricted = new ProvisioningRestrictedCommandExecutor(delegate);

        final Command<Void> command = Command.createRepository(Author.SYSTEM, "foo", "bar");
        assertThatThrownBy(() -> restricted.execute(command))
                .isInstanceOf(UnsupportedOperationException.class);
        verify(delegate, never()).execute(any(), any());
    }

    @Test
    void rejectsCreateCommandWrappedInForcePush() {
        final CommandExecutor delegate = mock(CommandExecutor.class);
        final CommandExecutor restricted = new ProvisioningRestrictedCommandExecutor(delegate);

        final Command<Void> command =
                Command.forcePush(Command.createRepository(Author.SYSTEM, "foo", "bar"));
        assertThatThrownBy(() -> restricted.execute(command))
                .isInstanceOf(UnsupportedOperationException.class);
        verify(delegate, never()).execute(any(), any());
    }

    @Test
    void forwardsOtherCommands() {
        final CommandExecutor delegate = mock(CommandExecutor.class);
        final CommandExecutor restricted = new ProvisioningRestrictedCommandExecutor(delegate);

        @SuppressWarnings("unchecked")
        final Command<Object> command = mock(Command.class);
        final CompletableFuture<Object> result = UnmodifiableFuture.completedFuture(new Object());
        when(delegate.execute(any(ExecutionContext.class), eq(command))).thenReturn(result);

        assertThat(restricted.execute(command)).isSameAs(result);
        verify(delegate).execute(any(ExecutionContext.class), eq(command));
    }

    @Test
    void forwardsLifecycleAndStatusMethods() {
        final CommandExecutor delegate = mock(CommandExecutor.class);
        final CommandExecutor restricted = new ProvisioningRestrictedCommandExecutor(delegate);

        when(delegate.replicaId()).thenReturn(42);
        when(delegate.isWritable()).thenReturn(true);
        assertThat(restricted.replicaId()).isEqualTo(42);
        assertThat(restricted.isWritable()).isTrue();

        restricted.setWritable(false);
        verify(delegate).setWritable(false);
        restricted.statusManager();
        verify(delegate).statusManager();
    }
}
