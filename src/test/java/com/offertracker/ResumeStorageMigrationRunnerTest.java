package com.offertracker;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.offertracker.entity.Resume;
import com.offertracker.mapper.ResumeMapper;
import com.offertracker.service.ResumeStorageMigrationRunner;
import com.offertracker.storage.ResumeFileStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResumeStorageMigrationRunnerTest {
    @TempDir
    Path tempDir;

    @Test
    void dryRunChecksLocalSourceWithoutUploadingOrUpdating() throws Exception {
        ResumeMapper mapper = mock(ResumeMapper.class);
        ResumeFileStorage storage = mock(ResumeFileStorage.class);
        Resume resume = localResume(17L, tempDir.resolve("17.pdf"));
        Files.write(Path.of(resume.getStoragePath()), new byte[] { 1, 2, 3 });
        Page<Resume> page = new Page<>();
        page.setRecords(List.of(resume));
        when(mapper.selectCount(isNull())).thenReturn(1L);
        when(mapper.selectPage(any(Page.class), isNull())).thenReturn(page);

        new ResumeStorageMigrationRunner(mapper, storage, tempDir.toString(), 100)
                .run(new DefaultApplicationArguments());

        verify(storage, never()).storeAt(any(), any());
        verify(mapper, never()).update(any(), any());
    }

    @Test
    void applyUsesStableObjectKeyAndConditionallyUpdatesLocator() throws Exception {
        ResumeMapper mapper = mock(ResumeMapper.class);
        ResumeFileStorage storage = mock(ResumeFileStorage.class);
        Resume resume = localResume(17L, tempDir.resolve("17.pdf"));
        Files.write(Path.of(resume.getStoragePath()), new byte[] { 1, 2, 3 });
        Page<Resume> page = new Page<>();
        page.setRecords(List.of(resume));
        when(mapper.selectCount(isNull())).thenReturn(1L);
        when(mapper.selectPage(any(Page.class), isNull())).thenReturn(page);
        when(storage.storeAt("legacy/17.pdf", new byte[] { 1, 2, 3 })).thenReturn("prefix/legacy/17.pdf");
        when(mapper.update(isNull(), any())).thenReturn(1);

        new ResumeStorageMigrationRunner(mapper, storage, tempDir.toString(), 100)
                .run(new DefaultApplicationArguments("--apply"));

        verify(storage).storeAt("legacy/17.pdf", new byte[] { 1, 2, 3 });
        verify(mapper).update(isNull(), any());
        org.junit.jupiter.api.Assertions.assertTrue(Files.exists(Path.of(resume.getStoragePath())));
    }

    private Resume localResume(Long id, Path path) {
        Resume resume = new Resume();
        resume.setId(id);
        resume.setStoragePath(path.toAbsolutePath().toString());
        return resume;
    }
}
