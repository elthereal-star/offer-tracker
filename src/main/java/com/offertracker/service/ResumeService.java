package com.offertracker.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.offertracker.common.BusinessException;
import com.offertracker.dto.ResumeResponse;
import com.offertracker.entity.Resume;
import com.offertracker.mapper.ResumeMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ResumeService {

    static final long MAX_SIZE_BYTES = 20L * 1024 * 1024;
    private final ResumeMapper resumeMapper;
    private final JobApplicationService applicationService;
    private final Path resumeDirectory;

    public ResumeService(ResumeMapper resumeMapper,
                         JobApplicationService applicationService,
                         @Value("${offer-tracker.storage.resume-dir:./data/resumes}") String resumeDirectory) {
        this.resumeMapper = resumeMapper;
        this.applicationService = applicationService;
        this.resumeDirectory = Path.of(resumeDirectory).toAbsolutePath().normalize();
    }

    @Transactional
    public ResumeResponse upload(MultipartFile file, Long applicationId) {
        validate(file);
        if (applicationId != null) applicationService.getOrThrow(applicationId);

        String originalFilename = sanitizeFilename(file.getOriginalFilename());
        Path storedFile = resumeDirectory.resolve(UUID.randomUUID() + ".pdf");
        try {
            Files.createDirectories(resumeDirectory);
            Files.copy(file.getInputStream(), storedFile, StandardCopyOption.REPLACE_EXISTING);
            String extractedText = extractText(file.getBytes());
            Resume resume = new Resume();
            resume.setApplicationId(applicationId);
            resume.setOriginalFilename(originalFilename);
            resume.setStoragePath(storedFile.toString());
            resume.setContentType("application/pdf");
            resume.setSizeBytes(file.getSize());
            resume.setExtractedText(extractedText);
            resume.setCreatedAt(LocalDateTime.now());
            resume.setUpdatedAt(LocalDateTime.now());
            resumeMapper.insert(resume);
            return ResumeResponse.from(resume);
        } catch (IOException ex) {
            deleteQuietly(storedFile);
            throw new BusinessException(400, "简历文件读取失败，请确认是有效的 PDF 文件");
        } catch (RuntimeException ex) {
            deleteQuietly(storedFile);
            throw ex;
        }
    }

    public List<ResumeResponse> list(Long applicationId) {
        LambdaQueryWrapper<Resume> query = new LambdaQueryWrapper<Resume>()
                .orderByDesc(Resume::getUpdatedAt);
        if (applicationId != null) query.eq(Resume::getApplicationId, applicationId);
        return resumeMapper.selectList(query).stream().map(ResumeResponse::from).toList();
    }

    public Resume getOrThrow(Long id) {
        Resume resume = resumeMapper.selectById(id);
        if (resume == null) throw new BusinessException(404, "简历不存在: " + id);
        return resume;
    }

    @Transactional
    public void delete(Long id) {
        Resume resume = getOrThrow(id);
        resumeMapper.deleteById(id);
        deleteQuietly(Path.of(resume.getStoragePath()));
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

    private void deleteQuietly(Path path) {
        try { Files.deleteIfExists(path); } catch (IOException ignored) { }
    }
}
