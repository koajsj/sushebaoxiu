package com.campus.repair.controller;

import com.campus.repair.common.Result;
import com.campus.repair.service.ImageService;
import com.campus.repair.vo.UserVO;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/images")
@lombok.RequiredArgsConstructor
public class ImageController {
    private final ImageService service;
    @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String,String>> upload(@AuthenticationPrincipal UserVO user, @RequestParam MultipartFile file) {
        return Result.success(service.upload(user,file));
    }
    @GetMapping("/{id}")
    public ResponseEntity<byte[]> read(@AuthenticationPrincipal UserVO user, @PathVariable String id) {
        var file=service.read(user,id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType())).cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options","nosniff").body(file.bytes());
    }
}
