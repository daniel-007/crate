/*
 * Licensed to CRATE Technology GmbH ("Crate") under one or more contributor
 * license agreements.  See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.  Crate licenses
 * this file to you under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.  You may
 * obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  See the
 * License for the specific language governing permissions and limitations
 * under the License.
 *
 * However, if you have executed another commercial license agreement
 * with Crate these terms will supersede the license and you may use the
 * software solely pursuant to the terms of the relevant commercial agreement.
 */

package io.crate.blob.v2;

import io.crate.blob.BlobContainer;
import io.crate.blob.stats.BlobStats;
import org.apache.lucene.util.IOUtils;
import org.elasticsearch.cluster.routing.ShardRouting;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.index.shard.AbstractIndexShardComponent;
import org.elasticsearch.index.shard.IndexShard;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class BlobShard extends AbstractIndexShardComponent {

    private static final String BLOBS_SUB_PATH = "blobs";

    private final BlobContainer blobContainer;
    private final IndexShard indexShard;

    @Inject
    public BlobShard(IndexShard indexShard, Path shardPath) {
        super(indexShard.shardId(), indexShard.indexSettings());
        this.indexShard = indexShard;
        File blobDir = shardPath.resolve(BLOBS_SUB_PATH).toFile();
        logger.info("creating BlobContainer at {}", blobDir);
        this.blobContainer = new BlobContainer(blobDir);
    }

    public byte[][] currentDigests(byte prefix) {
        return blobContainer.cleanAndReturnDigests(prefix);
    }

    public boolean delete(String digest) {
        return blobContainer.getFile(digest).delete();
    }

    public BlobContainer blobContainer() {
        return blobContainer;
    }

    public ShardRouting shardRouting() {
        return indexShard.routingEntry();
    }

    public BlobStats blobStats() {
        final BlobStats stats = new BlobStats();
        stats.location(blobContainer().getBaseDirectory().getAbsolutePath());
        for (File file : blobContainer().getFiles()) {
            stats.totalUsage(stats.totalUsage() + file.length());
            stats.count(stats.count() + 1);
        }
        return stats;
    }

    void deletePath() {
        File dir = blobContainer.getBaseDirectory();
        if (dir.exists()){
            logger.debug("[{}] Deleting blob shard directory '{}'", dir);
            try {
                IOUtils.rm(blobContainer.getBaseDirectory().toPath());
            } catch (IOException e) {
                logger.warn("Could not delete blob shard directory {} {}", dir, e);
            }
        } else {
            logger.warn("wanted to delete blob shard directory {} but it was already gone", dir);
        }
    }
}
