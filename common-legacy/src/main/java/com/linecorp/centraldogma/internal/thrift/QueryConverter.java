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

package com.linecorp.centraldogma.internal.thrift;

import java.util.Collections;

import com.google.common.base.Converter;

/**
 * Provides a function converting back and forth between {@link Query} and
 * {@link com.linecorp.centraldogma.common.Query}.
 */
public final class QueryConverter extends Converter<com.linecorp.centraldogma.common.Query<?>, Query> {
    public static final Converter<com.linecorp.centraldogma.common.Query<?>, Query> TO_DATA =
            new QueryConverter();

    public static final Converter<Query, com.linecorp.centraldogma.common.Query<?>> TO_MODEL =
            TO_DATA.reverse();

    private QueryConverter() {}

    @Override
    protected Query doForward(com.linecorp.centraldogma.common.Query<?> query) {
        switch (query.type()) {
            case IDENTITY:
                return new Query(query.path(), QueryType.IDENTITY, Collections.emptyList());
            case IDENTITY_TEXT:
                return new Query(query.path(), QueryType.IDENTITY_TEXT, Collections.emptyList());
            case IDENTITY_JSON:
                return new Query(query.path(), QueryType.IDENTITY_JSON, Collections.emptyList());
            case JSON_PATH:
                return new Query(query.path(), QueryType.JSON_PATH, query.expressions());
        }

        throw new Error();
    }

    @Override
    protected com.linecorp.centraldogma.common.Query<?> doBackward(Query query) {
        switch (query.getType()) {
            case IDENTITY:
            case IDENTITY_TEXT:
            case IDENTITY_JSON:
                // Apply the same approach used in QueryRequestConverter.
                // This workaround is needed to let users read a JSON data as a text.
                return com.linecorp.centraldogma.common.Query.of(
                        com.linecorp.centraldogma.common.QueryType.IDENTITY, query.getPath());
            case JSON_PATH:
                return com.linecorp.centraldogma.common.Query.ofJsonPath(query.getPath(),
                                                                         query.getExpressions());
        }

        throw new Error();
    }
}
