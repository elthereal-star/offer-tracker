package com.offertracker.controller;

import com.offertracker.common.ApiResponse;
import com.offertracker.dto.BackupData;
import com.offertracker.dto.ImportPreview;
import com.offertracker.dto.ImportResult;
import com.offertracker.service.DataTransferService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/data")
public class DataTransferController {

    private final DataTransferService dataTransferService;

    public DataTransferController(DataTransferService dataTransferService) {
        this.dataTransferService = dataTransferService;
    }

    @GetMapping(value = "/export", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<BackupData> exportBackup() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("offer-tracker-backup.json").build().toString())
                .body(dataTransferService.exportBackup());
    }

    @GetMapping(value = "/export.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> exportCsv() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("offer-tracker-applications.csv",
                                StandardCharsets.UTF_8).build().toString())
                .body(dataTransferService.exportApplicationsCsv());
    }

    @PostMapping("/import/validate")
    public ApiResponse<ImportPreview> validateImport(@RequestBody BackupData backup) {
        return ApiResponse.ok(dataTransferService.validate(backup));
    }

    @PostMapping("/import")
    public ApiResponse<ImportResult> importBackup(
            @RequestBody BackupData backup,
            @RequestParam(defaultValue = "false") boolean replaceExisting) {
        return ApiResponse.ok(dataTransferService.importBackup(backup, replaceExisting));
    }
}
