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
package com.linecorp.centraldogma.server.storage.project;

import java.util.concurrent.CompletableFuture;

import com.linecorp.centraldogma.common.Author;
import com.linecorp.centraldogma.common.Revision;
import com.linecorp.centraldogma.server.command.Command;
import com.linecorp.centraldogma.server.metadata.MetadataService;
import com.linecorp.centraldogma.server.metadata.ProjectRoles;
import com.linecorp.centraldogma.server.metadata.RepositoryMetadata;

/**
 * Creates projects and repositories, taking care of the cross-cutting concerns that a raw
 * {@link Command} does not: registering the {@link MetadataService metadata} of the new project or repository
 * and, when encryption at rest is enabled, generating and storing the wrapped data encryption key so that the
 * storage is encrypted.
 *
 * <p>This is the single entry point that server-side plugins and internal services should use to create
 * projects and repositories, instead of executing {@link Command#createProject(Author, String)} or
 * {@link Command#createRepository(Author, String, String)} and wiring up encryption and metadata by hand.
 */
public interface ProjectProvisioner {

    /**
     * Creates a new project with the specified {@code projectName}. If encryption at rest is enabled in the
     * server, the project is created with an encrypted storage.
     */
    CompletableFuture<Void> createProject(Author author, String projectName);

    /**
     * Creates a new repository with the specified {@code repoName} in the specified {@code projectName}.
     * The {@linkplain RepositoryMetadata#DEFAULT_PROJECT_ROLES default project roles} are used, the
     * {@code author} is granted the {@link com.linecorp.centraldogma.common.RepositoryRole#ADMIN} role, and the
     * repository is encrypted if the project is encrypted.
     */
    CompletableFuture<Revision> createRepository(Author author, String projectName, String repoName);

    /**
     * Creates a new repository with the specified {@code repoName} in the specified {@code projectName}.
     *
     * @param projectRoles the {@link ProjectRoles} to grant to the members and guests of the project
     * @param assignRoleToAuthor whether to grant the {@code author} the
     *                           {@link com.linecorp.centraldogma.common.RepositoryRole#ADMIN} role on the new
     *                           repository
     * @param encrypt whether to encrypt the repository. The repository is also encrypted when the project is
     *                encrypted, regardless of this flag.
     */
    CompletableFuture<Revision> createRepository(Author author, String projectName, String repoName,
                                                 ProjectRoles projectRoles, boolean assignRoleToAuthor,
                                                 boolean encrypt);
}
