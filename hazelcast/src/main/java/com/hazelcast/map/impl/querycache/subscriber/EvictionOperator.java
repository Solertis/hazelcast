/*
 * Copyright (c) 2008, Hazelcast, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hazelcast.map.impl.querycache.subscriber;

import com.hazelcast.config.EvictionConfig;
import com.hazelcast.config.QueryCacheConfig;
import com.hazelcast.internal.eviction.EvictionChecker;
import com.hazelcast.internal.eviction.EvictionListener;
import com.hazelcast.internal.eviction.EvictionPolicyEvaluator;
import com.hazelcast.internal.eviction.EvictionStrategy;
import com.hazelcast.internal.eviction.MaxSizeChecker;
import com.hazelcast.map.impl.querycache.subscriber.record.QueryCacheRecord;
import com.hazelcast.nio.serialization.Data;

import static com.hazelcast.internal.config.ConfigValidator.checkEvictionConfig;
import static com.hazelcast.internal.eviction.EvictionPolicyEvaluatorProvider.getEvictionPolicyEvaluator;
import static com.hazelcast.internal.eviction.EvictionStrategyProvider.getEvictionStrategy;

/**
 * Contains eviction specific functionality of a {@link QueryCacheRecordStore}.
 */
public class EvictionOperator {

    private final QueryCacheRecordHashMap cache;
    private final EvictionConfig evictionConfig;
    private final MaxSizeChecker maxSizeChecker;
    private final EvictionPolicyEvaluator<Data, QueryCacheRecord> evictionPolicyEvaluator;
    private final EvictionChecker evictionChecker;
    private final EvictionStrategy<Data, QueryCacheRecord, QueryCacheRecordHashMap> evictionStrategy;
    private final EvictionListener<Data, QueryCacheRecord> listener;
    private final ClassLoader classLoader;

    public EvictionOperator(QueryCacheRecordHashMap cache,
                            QueryCacheConfig config,
                            EvictionListener<Data, QueryCacheRecord> listener,
                            ClassLoader classLoader) {
        this.cache = cache;
        this.evictionConfig = config.getEvictionConfig();
        this.maxSizeChecker = createCacheMaxSizeChecker();
        this.evictionPolicyEvaluator = createEvictionPolicyEvaluator();
        this.evictionChecker = createEvictionChecker();
        this.evictionStrategy = createEvictionStrategy();
        this.listener = listener;
        this.classLoader = classLoader;
    }

    boolean isEvictionEnabled() {
        return evictionStrategy != null && evictionPolicyEvaluator != null;
    }

    int evictIfRequired() {
        int evictedCount = 0;
        if (isEvictionEnabled()) {
            evictedCount = evictionStrategy.evict(cache, evictionPolicyEvaluator, evictionChecker, listener);
        }
        return evictedCount;
    }

    private MaxSizeChecker createCacheMaxSizeChecker() {
        return new MaxSizeChecker() {
            @Override
            public boolean isReachedToMaxSize() {
                return cache.size() > evictionConfig.getSize();
            }
        };
    }

    private EvictionPolicyEvaluator<Data, QueryCacheRecord> createEvictionPolicyEvaluator() {
        checkEvictionConfig(evictionConfig, false);
        return getEvictionPolicyEvaluator(evictionConfig, classLoader);
    }

    private EvictionStrategy<Data, QueryCacheRecord, QueryCacheRecordHashMap> createEvictionStrategy() {
        return getEvictionStrategy(evictionConfig);
    }

    private EvictionChecker createEvictionChecker() {
        return new MaxSizeEvictionChecker();
    }

    /**
     * Checks whether the eviction is startable.
     * It should be started when cache reaches the configured max-size.
     */
    private class MaxSizeEvictionChecker implements EvictionChecker {

        @Override
        public boolean isEvictionRequired() {
            return maxSizeChecker != null && maxSizeChecker.isReachedToMaxSize();
        }
    }
}
