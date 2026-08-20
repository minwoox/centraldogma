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
package com.linecorp.centraldogma.server.internal.storage.project;

import static com.linecorp.centraldogma.server.metadata.RepositoryMetadata.DEFAULT_PROJECT_ROLES;
import static java.util.concurrent.ForkJoinPool.commonPool;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;

import com.google.common.collect.ImmutableMap;

import com.linecorp.armeria.common.metric.NoopMeterRegistry;
import com.linecorp.centraldogma.common.Author;
import com.linecorp.centraldogma.common.RepositoryRole;
import com.linecorp.centraldogma.common.ShuttingDownException;
import com.linecorp.centraldogma.internal.Util;
import com.linecorp.centraldogma.server.CentralDogmaBuilder;
import com.linecorp.centraldogma.server.command.Command;
import com.linecorp.centraldogma.server.command.CommandExecutor;
import com.linecorp.centraldogma.server.command.StandaloneCommandExecutor;
import com.linecorp.centraldogma.server.management.ServerStatusManager;
import com.linecorp.centraldogma.server.metadata.MetadataService;
import com.linecorp.centraldogma.server.metadata.ProjectRoles;
import com.linecorp.centraldogma.server.metadata.RepositoryMetadata;
import com.linecorp.centraldogma.server.storage.encryption.EncryptionStorageManager;
import com.linecorp.centraldogma.server.storage.encryption.NoopEncryptionStorageManager;
import com.linecorp.centraldogma.server.storage.project.InternalProjectInitializer;
import com.linecorp.centraldogma.server.storage.project.Project;
import com.linecorp.centraldogma.server.storage.project.ProjectManager;
import com.linecorp.centraldogma.testing.internal.ProjectManagerExtension;

class ProjectApiManagerTest {

    private static final String PROJECT = "foo";

    private static final Author USER_AUTHOR = Author.ofEmail("user@localhost.localdomain");
    private static final Author APP_AUTHOR =
            new Author("app-1", "app-1" + Util.APP_IDENTITY_EMAIL_SUFFIX);

    @RegisterExtension
    final ProjectManagerExtension manager = new ProjectManagerExtension() {
        @Override
        protected void afterExecutorStarted() {
            executor().execute(Command.createProject(USER_AUTHOR, PROJECT)).join();
        }

        @Override
        protected boolean runForEachTest() {
            return true;
        }
    };

    private MetadataService mds;

    private ProjectApiManager newProjectApiManager() {
        mds = new MetadataService(manager.projectManager(), manager.executor(),
                                  manager.internalProjectInitializer());
        return new ProjectApiManager(manager.projectManager(), manager.executor(), mds,
                                     NoopEncryptionStorageManager.INSTANCE);
    }

    @Test
    void simpleOverloadUsesDefaultRolesAndGrantsAuthorAdmin() {
        final ProjectApiManager projectApiManager = newProjectApiManager();
        projectApiManager.createRepository(USER_AUTHOR, PROJECT, "repo1").join();

        final RepositoryMetadata metadata = mds.getRepo(PROJECT, "repo1").join();
        assertThat(metadata.roles().projectRoles()).isEqualTo(DEFAULT_PROJECT_ROLES);
        // WRITE for members, no role for guests.
        assertThat(metadata.roles().projectRoles().member()).isEqualTo(RepositoryRole.WRITE);
        assertThat(metadata.roles().projectRoles().guest()).isNull();
        // The author is granted the ADMIN role on the new repository.
        assertThat(metadata.roles().users())
                .isEqualTo(ImmutableMap.of(USER_AUTHOR.email(), RepositoryRole.ADMIN));
    }

    @Test
    void customProjectRolesArePreserved() {
        final ProjectApiManager projectApiManager = newProjectApiManager();
        final ProjectRoles projectRoles = ProjectRoles.of(RepositoryRole.READ, null);
        projectApiManager.createRepository(USER_AUTHOR, PROJECT, "repo2", projectRoles, false, false).join();

        final RepositoryMetadata metadata = mds.getRepo(PROJECT, "repo2").join();
        assertThat(metadata.roles().projectRoles()).isEqualTo(projectRoles);
        assertThat(metadata.roles().projectRoles().member()).isEqualTo(RepositoryRole.READ);
        assertThat(metadata.roles().projectRoles().guest()).isNull();
    }

    @Test
    void customProjectRolesWithGuestRole() {
        final ProjectApiManager projectApiManager = newProjectApiManager();
        final ProjectRoles projectRoles = ProjectRoles.of(RepositoryRole.WRITE, RepositoryRole.READ);
        projectApiManager.createRepository(USER_AUTHOR, PROJECT, "repo3", projectRoles, false, false).join();

        final RepositoryMetadata metadata = mds.getRepo(PROJECT, "repo3").join();
        assertThat(metadata.roles().projectRoles().member()).isEqualTo(RepositoryRole.WRITE);
        assertThat(metadata.roles().projectRoles().guest()).isEqualTo(RepositoryRole.READ);
    }

    @Test
    void notAssigningRoleToAuthorLeavesUserAndAppRolesEmpty() {
        final ProjectApiManager projectApiManager = newProjectApiManager();
        projectApiManager.createRepository(USER_AUTHOR, PROJECT, "repo4",
                                           ProjectRoles.of(RepositoryRole.READ, null), false, false).join();

        final RepositoryMetadata metadata = mds.getRepo(PROJECT, "repo4").join();
        assertThat(metadata.roles().users()).isEmpty();
        assertThat(metadata.roles().appIds()).isEmpty();
    }

    @Test
    void assigningRoleToUserAuthorGrantsAdminToUser() {
        final ProjectApiManager projectApiManager = newProjectApiManager();
        projectApiManager.createRepository(USER_AUTHOR, PROJECT, "repo5",
                                           ProjectRoles.of(RepositoryRole.READ, null), true, false).join();

        final RepositoryMetadata metadata = mds.getRepo(PROJECT, "repo5").join();
        assertThat(metadata.roles().users())
                .isEqualTo(ImmutableMap.of(USER_AUTHOR.email(), RepositoryRole.ADMIN));
        assertThat(metadata.roles().appIds()).isEmpty();
    }

    @Test
    void assigningRoleToAppAuthorGrantsAdminToApp() {
        final ProjectApiManager projectApiManager = newProjectApiManager();
        projectApiManager.createRepository(APP_AUTHOR, PROJECT, "repo6",
                                           ProjectRoles.of(RepositoryRole.READ, null), true, false).join();

        final RepositoryMetadata metadata = mds.getRepo(PROJECT, "repo6").join();
        assertThat(metadata.roles().appIds())
                .isEqualTo(ImmutableMap.of(APP_AUTHOR.name(), RepositoryRole.ADMIN));
        assertThat(metadata.roles().users()).isEmpty();
    }

    @Test
    void createRepositoryInNonEncryptedProjectIsNotEncrypted() {
        final ProjectApiManager projectApiManager = newProjectApiManager();
        projectApiManager.createRepository(USER_AUTHOR, PROJECT, "repo7",
                                           ProjectRoles.of(RepositoryRole.READ, null), false, false).join();
        final Project project = manager.projectManager().get(PROJECT);
        assertThat(project.repos().exists("repo7")).isTrue();
        assertThat(project.repos().get("repo7").isEncrypted()).isFalse();
    }

    @Test
    void createProjectCreatesProjectAndMetadata() {
        final ProjectApiManager projectApiManager = newProjectApiManager();
        projectApiManager.createProject(USER_AUTHOR, "newProject").join();

        assertThat(manager.projectManager().exists("newProject")).isTrue();
        // Metadata is created, so a repository can be registered right away.
        projectApiManager.createRepository(USER_AUTHOR, "newProject", "repo").join();
        assertThat(manager.projectManager().get("newProject").repos().exists("repo")).isTrue();
        assertThat(manager.projectManager().get("newProject").repos().get(Project.REPO_DOGMA).isEncrypted())
                .isFalse();
    }

    /**
     * Verifies that the provisioner encrypts projects and repositories when encryption at rest is enabled,
     * with the whole stack (project manager, command executor and project initializer) sharing a real
     * {@link EncryptionStorageManager}.
     */
    @Nested
    class EncryptionEnabled {

        @TempDir
        File rootDir;

        private ScheduledExecutorService purgeWorker;
        private EncryptionStorageManager encryptionStorageManager;
        private ProjectManager projectManager;
        private CommandExecutor executor;
        private ProjectApiManager projectApiManager;

        @BeforeEach
        void setUp() throws Exception {
            final File dataDir = new File(rootDir, "data");
            purgeWorker = Executors.newSingleThreadScheduledExecutor();
            encryptionStorageManager = EncryptionStorageManager.of(
                    new File(rootDir, "rocksdb").toPath(), false, "kekId");
            assertThat(encryptionStorageManager.enabled()).isTrue();
            projectManager = new DefaultProjectManager(
                    dataDir, commonPool(), purgeWorker, NoopMeterRegistry.get(),
                    CentralDogmaBuilder.DEFAULT_REPOSITORY_CACHE_SPEC, encryptionStorageManager,
                    ImmutableMap.of());
            executor = new StandaloneCommandExecutor(projectManager, commonPool(),
                                                     new ServerStatusManager(dataDir), null,
                                                     encryptionStorageManager, null, null, null, null);
            executor.start().get();
            final InternalProjectInitializer projectInitializer =
                    new InternalProjectInitializer(executor, projectManager, encryptionStorageManager);
            projectInitializer.initialize();
            final MetadataService mds = new MetadataService(projectManager, executor, projectInitializer);
            projectApiManager = new ProjectApiManager(projectManager, executor, mds,
                                                      encryptionStorageManager);
        }

        @AfterEach
        void tearDown() {
            if (executor != null) {
                executor.stop();
            }
            if (projectManager != null) {
                projectManager.close(ShuttingDownException::new);
            }
            if (purgeWorker != null) {
                purgeWorker.shutdownNow();
            }
            if (encryptionStorageManager != null) {
                encryptionStorageManager.close();
            }
        }

        @Test
        void createProjectEncryptsTheProject() {
            projectApiManager.createProject(USER_AUTHOR, "encProject").join();
            final Project project = projectManager.get("encProject");
            assertThat(project.repos().get(Project.REPO_DOGMA).isEncrypted()).isTrue();
        }

        @Test
        void createRepositoryInheritsEncryptionFromProject() {
            projectApiManager.createProject(USER_AUTHOR, "encProject").join();
            // The caller did not request encryption, but the repository must be encrypted because the project
            // is encrypted.
            projectApiManager.createRepository(USER_AUTHOR, "encProject", "encRepo").join();
            assertThat(projectManager.get("encProject").repos().get("encRepo").isEncrypted()).isTrue();
        }

        @Test
        void rawCreateRepositoryCommandInEncryptedProjectIsRejected() {
            projectApiManager.createProject(USER_AUTHOR, "encProject").join();
            // Executing the raw command bypasses the provisioner; the executor must reject it because the
            // project is encrypted, so the repository data would otherwise be stored in plaintext.
            assertThatThrownBy(() -> executor.execute(
                    Command.createRepository(USER_AUTHOR, "encProject", "plainRepo")).join())
                    .hasMessageContaining("must be encrypted");
            assertThat(projectManager.get("encProject").repos().exists("plainRepo")).isFalse();
        }
    }
}
