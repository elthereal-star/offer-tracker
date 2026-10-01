package com.offertracker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offertracker.common.BusinessException;
import com.offertracker.common.CurrentUserContext;
import com.offertracker.dto.ResumeResponse;
import com.offertracker.entity.Resume;
import com.offertracker.mapper.ResumeMapper;
import com.offertracker.storage.ResumeFileStorage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ResumeService {

    static final long MAX_SIZE_BYTES = 20L * 1024 * 1024;
    private final ResumeMapper resumeMapper;
    private final JobApplicationService applicationService;
    private final ResumeFileStorage storage;

    public ResumeService(ResumeMapper resumeMapper,
                         JobApplicationService applicationService,
                         ResumeFileStorage storage) {
        this.resumeMapper = resumeMapper;
        this.applicationService = applicationService;
        this.storage = storage;
    }

    @Transactional
    public ResumeResponse upload(MultipartFile file, Long applicationId) {
        validate(file);
        if (applicationId != null) applicationService.getOrThrow(applicationId);

        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        String extractedText;
        byte[] content;
        try {
            content = file.getBytes();
            extractedText = extractText(content);
        } catch (IOException ex) {
            throw new BusinessException(400, "简历文件读取失败，请确认是有效的 PDF 文件");
        }

        String storedFile;
        try {
            storedFile = storage.store(content);
        } catch (IOException | RuntimeException ex) {
            throw new BusinessException(500, "简历文件保存失败，请稍后重试");
        }
        try {
            Resume resume = new Resume();
            if (CurrentUserContext.get() != null) resume.setOwnerId(CurrentUserContext.get().id());
            resume.setApplicationId(applicationId);
            resume.setOriginalFilename(originalFilename);
            resume.setStoragePath(storedFile);
            resume.setContentType("application/pdf");
            resume.setSizeBytes(file.getSize());
            resume.setExtractedText(extractedText);
            resume.setCreatedAt(LocalDateTime.now());
            resume.setUpdatedAt(LocalDateTime.now());
            resumeMapper.insert(resume);
            cleanupUploadIfTransactionRollsBack(storedFile);
            return ResumeResponse.from(resume);
        } catch (RuntimeException ex) {
            deleteQuietly(storedFile);
            throw ex;
        }
    }

    public List<ResumeResponse> list(Long applicationId) {
        LambdaQueryWrapper<Resume> query = new LambdaQueryWrapper<Resume>()
                .orderByDesc(Resume::getUpdatedAt);
        if (applicationId != null) query.eq(Resume::getApplicationId, applicationId);
        if (CurrentUserContext.get() != null) query.eq(Resume::getOwnerId, CurrentUserContext.get().id());
        return resumeMapper.selectList(query).stream().map(ResumeResponse::from).toList();
    }

    public Resume getOrThrow(Long id) {
        Resume resume = resumeMapper.selectById(id);
        if (resume == null) throw new BusinessException(404, "简历不存在: " + id);
        if (CurrentUserContext.get() != null && !CurrentUserContext.get().id().equals(resume.getOwnerId())) throw new BusinessException(404, "简历不存在: " + id);
        return resume;
    }

    @Transactional
    public void delete(Long id) {
        Resume resume = getOrThrow(id);
        resumeMapper.deleteById(id);
        deleteFileAfterTransactionCommits(resume.getStoragePath());
    }

    public ResumeFile download(Long id) {
        Resume resume = getOrThrow(id);
        try { return new ResumeFile(resume, storage.load(resume.getStoragePath())); }
        catch (IOException | RuntimeException ex) { throw new BusinessException(404, "简历文件不存在或暂时无法读取"); }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new BusinessException(400, "请上传 PDF 简历");
        if (file.getSize() > MAX_SIZE_BYTES) throw new BusinessException(400, "简历大小不能超过 20MB");
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        if (!filename.endsWith(".pdf") || !contentType.equals("application/pdf")) {
            throw new BusinessException(400, "目前只支持 PDF 格式的简历");
        }
    }

    private String extractText(byte[] content) throws IOException {
        try (PDDocument document = Loader.loadPDF(content)) {
            String text = new PDFTextStripper().getText(document).trim();
            if (text.isBlank()) throw new BusinessException(400, "PDF 中没有可提取的文本，请上传文字版简历");
            return text;
        }
    }

    private String sanitizeFilename(String filename) {
        String safe = filename == null ? "resume.pdf" : Path.of(filename).getFileName().toString();
        return safe.isBlank() ? "resume.pdf" : safe;
    }

    private void deleteQuietly(String locator) {
        try { storage.delete(locator); } catch (IOException | RuntimeException ignored) { }
    }

    private void cleanupUploadIfTransactionRollsBack(String locator) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) deleteQuietly(locator);
            }
        });
    }

    private void deleteFileAfterTransactionCommits(String locator) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            deleteQuietly(locator);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() { deleteQuietly(locator); }
        });
    }

    public record ResumeFile(Resume resume, InputStream content) { }
}
