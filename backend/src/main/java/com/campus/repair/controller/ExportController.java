package com.campus.repair.controller;

import com.campus.repair.service.ExportService;
import com.campus.repair.service.OrderAccessService;
import com.campus.repair.vo.UserVO;
import java.nio.charset.StandardCharsets;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/export")
@lombok.RequiredArgsConstructor
public class ExportController {
    private final ExportService service;
    private final OrderAccessService access;

    @GetMapping("/orders")
    public ResponseEntity<byte[]> orders(@AuthenticationPrincipal UserVO user) {
        access.requireAdmin(user);
        return download(service.orders(user));
    }
    @GetMapping("/workers")
    public ResponseEntity<byte[]> workers(@AuthenticationPrincipal UserVO user) {
        access.requireAdmin(user);
        return download(service.workers(user));
    }
    @GetMapping("/types")
    public ResponseEntity<byte[]> types(@AuthenticationPrincipal UserVO user) {
        access.requireAdmin(user);
        return download(service.types(user));
    }

    private ResponseEntity<byte[]> download(ExportService.ReportFile report) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .contentLength(report.bytes().length).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,ContentDisposition.attachment().filename(report.filename(),StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options","nosniff").body(report.bytes());
    }
}
