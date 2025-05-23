/*
 * Copyright 2017 LINE Corporation
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

package com.linecorp.centraldogma.server.internal.api;

import static java.util.Objects.requireNonNull;

import java.util.Map;

import javax.annotation.Nullable;

import com.linecorp.centraldogma.common.Author;
import com.linecorp.centraldogma.common.Change;
import com.linecorp.centraldogma.common.Commit;
import com.linecorp.centraldogma.common.Entry;
import com.linecorp.centraldogma.common.EntryType;
import com.linecorp.centraldogma.common.MergedEntry;
import com.linecorp.centraldogma.common.ProjectRole;
import com.linecorp.centraldogma.common.RepositoryStatus;
import com.linecorp.centraldogma.common.Revision;
import com.linecorp.centraldogma.internal.api.v1.ChangeDto;
import com.linecorp.centraldogma.internal.api.v1.CommitDto;
import com.linecorp.centraldogma.internal.api.v1.CommitMessageDto;
import com.linecorp.centraldogma.internal.api.v1.EntryDto;
import com.linecorp.centraldogma.internal.api.v1.MergedEntryDto;
import com.linecorp.centraldogma.internal.api.v1.ProjectDto;
import com.linecorp.centraldogma.internal.api.v1.PushResultDto;
import com.linecorp.centraldogma.internal.api.v1.RepositoryDto;
import com.linecorp.centraldogma.server.metadata.RepositoryMetadata;
import com.linecorp.centraldogma.server.storage.project.Project;
import com.linecorp.centraldogma.server.storage.repository.Repository;

/**
 * A utility class to convert domain objects to DTO objects.
 */
final class DtoConverter {

    public static ProjectDto convert(Project project, ProjectRole userRole) {
        requireNonNull(project, "project");
        return new ProjectDto(project.name(), project.author(), userRole, project.creationTimeMillis());
    }

    public static RepositoryDto convert(Repository repository,
                                        Map<String, RepositoryMetadata> metadataMap) {
        requireNonNull(repository, "repository");
        final RepositoryStatus status;
        if (metadataMap == null) {
            status = RepositoryStatus.ACTIVE;
        } else {
            final RepositoryMetadata metadata = metadataMap.get(repository.name());
            if (metadata == null) {
                status = RepositoryStatus.ACTIVE;
            } else {
                status = metadata.status();
            }
        }
        return convert(repository, status);
    }

    public static RepositoryDto convert(Repository repository, RepositoryStatus status) {
        final Revision headRevision = repository.normalizeNow(Revision.HEAD);
        final String projectName = repository.parent().name();
        return new RepositoryDto(projectName, repository.name(), repository.author(), headRevision,
                                 repository.creationTimeMillis(), status);
    }

    public static <T> EntryDto<T> convert(Repository repository, Revision revision,
                                          Entry<T> entry, boolean withContent) {
        requireNonNull(entry, "entry");
        if (withContent && entry.hasContent()) {
            return convert(repository, revision, entry.path(), entry.type(), entry.content());
        }
        return convert(repository, revision, entry.path(), entry.type());
    }

    private static <T> EntryDto<T> convert(Repository repository, Revision revision,
                                           String path, EntryType type) {
        return convert(repository, revision, path, type, null);
    }

    private static <T> EntryDto<T> convert(Repository repository, Revision revision, String path,
                                           EntryType type, @Nullable T content) {
        requireNonNull(repository, "repository");
        return new EntryDto<>(requireNonNull(revision, "revision"),
                              requireNonNull(path, "path"),
                              requireNonNull(type, "type"),
                              repository.parent().name(),
                              repository.name(),
                              content);
    }

    public static PushResultDto convert(Revision revision, long commitTimeMillis) {
        return new PushResultDto(revision, commitTimeMillis);
    }

    public static CommitDto convert(Commit commit) {
        requireNonNull(commit, "commit");

        return convert(commit.revision(), commit.author(),
                       new CommitMessageDto(commit.summary(), commit.detail(), commit.markup()),
                       commit.when());
    }

    public static CommitDto convert(Revision revision, Author author, CommitMessageDto commitMessage,
                                    long commitTimeMillis) {
        return new CommitDto(revision, author, commitMessage, commitTimeMillis);
    }

    public static <T> ChangeDto<T> convert(Change<T> change) {
        requireNonNull(change, "change");
        return new ChangeDto<>(change.path(), change.type(), change.content());
    }

    public static <T> MergedEntryDto<T> convert(MergedEntry<T> mergedEntry) {
        requireNonNull(mergedEntry, "mergedEntry");
        return new MergedEntryDto<>(mergedEntry.revision(), mergedEntry.type(),
                                    mergedEntry.content(), mergedEntry.paths());
    }

    private DtoConverter() {}
}
