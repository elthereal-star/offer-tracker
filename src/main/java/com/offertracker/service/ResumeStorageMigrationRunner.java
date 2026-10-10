package com.offertracker.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.offertracker.entity.Resume;
import com.offertracker.mapper.ResumeMapper;
import com.offertracker.storage.ResumeFileStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

@Component
@ConditionalOnProperty(name = "offer-tracker.resume-migration.enabled", havingValue = "true")
public class ResumeStorageMigrationRunner implements ApplicationRunner {
    private static final long MAX_RESUME_BYTES = 20L * 1024 * 1024;

    private final ResumeMapper resumeMapper;
    private final ResumeFileStorage targetStorage;
    private final Path sourceDirectory;
    private final int batchSize;

    public ResumeStorageMigrationRunner(ResumeMapper resumeMapper,
                                        ResumeFileStorage targetStorage,
                                        @Value("${offer-tracker.storage.resume-dir:./data/resumes}") String sourceDirectory,
                                        @Value("${offer-tracker.resume-migration.batch-size:100}") int batchSize) {
        this.resumeMapper = resumeMapper;
        this.targetStorage = targetStorage;
        this.sourceDirectory = Path.of(sourceDirectory).toAbsolutePath().normalize();
        this.batchSize = Math.max(1, Math.min(batchSize, 500));
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean apply = args.containsOption("apply");
        long total = resumeMapper.selectCount(null);
        long scanned = 0;
        long migrated = 0;
        long planned = 0;
        long skipped = 0;
        long failed = 0;
        long pages = (total + batchSize - 1) / batchSize;

        for (long pageNumber = 1; pageNumber <= pages; pageNumber++) {
            List<Resume> resumes = resumeMapper.selectPage(new Page<>(pageNumber, batchSize), null).getRecords();
            for (Resume resume : resumes) {
                scanned++;
                try {
                    MigrationResult result = migrate(resume, apply);
                    if (result == MigrationResult.MIGRATED) migrated++;
                    else if (result == MigrationResult.PLANNED) planned++;
                    else skipped++;
                } catch (Exception ex) {
                    failed++;
                    System.err.printf("Resume migration failed for record %d (%s)%n", resume.getId(), ex.getClass().getSimpleName());
                }
            }
        }
        System.out.printf("Resume migration %s: scanned=%d migrated=%d planned=%d skipped=%d failed=%d%n",
                apply ? "applied" : "dry-run", scanned, migrated, planned, skipped, failed);
        if (failed > 0) throw new IllegalStateException("Resume migration had failures; inspect the safe error summary above");
    }

    private MigrationResult migrate(Resume resume, boolean apply) throws IOException {
        String oldLocator = resume.getStoragePath();
        if (oldLocator == null || oldLocator.isBlank()) return MigrationResult.SKIPPED;

        Path source;
        try {
            source = Path.of(oldLocator).toAbsolutePath().normalize();
        } catch (RuntimeException ex) {
            return MigrationResult.SKIPPED;
        }
        if (!source.startsWith(sourceDirectory) || source.equals(sourceDirectory)) return MigrationResult.SKIPPED;
        if (!Files.isRegularFile(source)) throw new IOException("source file is unavailable");
        Path realRoot = sourceDirectory.toRealPath();
        source = source.toRealPath();
        if (!source.startsWith(realRoot) || source.equals(realRoot)) return MigrationResult.SKIPPED;
        long size = Files.size(source);
        if (size <= 0 || size > MAX_RESUME_BYTES) throw new IOException("source file size is outside the supported range");
        if (!apply) return MigrationResult.PLANNED;

        String newLocator = targetStorage.storeAt("legacy/" + resume.getId() + ".pdf", Files.readAllBytes(source));
        int updated = resumeMapper.update(null, new UpdateWrapper<Resume>()
                .eq("id", resume.getId())
                .eq("storage_path", oldLocator)
                .set("storage_path", newLocator)
                .set("updated_at", LocalDateTime.now()));
        return updated == 1 ? MigrationResult.MIGRATED : MigrationResult.SKIPPED;
    }

    enum MigrationResult { MIGRATED, PLANNED, SKIPPED }
}
