package com.offertracker.service;

import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.stereotype.Component;

import java.time.Instant;
import org.springframework.data.domain.Sort;

@Component
@Profile("mongo-archive")
public class MongoAiRuntimeArchive implements AiRuntimeArchive {
    private final MongoTemplate mongo;
    public MongoAiRuntimeArchive(MongoTemplate mongo) { this.mongo = mongo; }
    @Override public void append(Long sessionId, String stateJson, long version) {
        mongo.save(new SnapshotDocument(sessionId, version, stateJson, Instant.now()));
    }
    @Override public String latest(Long sessionId) {
        SnapshotDocument doc = mongo.query(SnapshotDocument.class).matching(
                org.springframework.data.mongodb.core.query.Query.query(
                        org.springframework.data.mongodb.core.query.Criteria.where("sessionId").is(sessionId))
                        .with(Sort.by(Sort.Direction.DESC, "version")).limit(1)).first().orElse(null);
        return doc == null ? null : doc.stateJson();
    }
    @Document("ai_interview_runtime_snapshots")
    public record SnapshotDocument(Long sessionId, long version, String stateJson, Instant updatedAt) { }
}
