package com.campus.repair.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.campus.repair.common.BusinessException;
import com.campus.repair.common.ErrorCode;
import com.campus.repair.entity.RepairImageEntity;
import com.campus.repair.mapper.RepairImageMapper;
import com.campus.repair.mapper.RepairOrderMapper;
import com.campus.repair.security.UserRole;
import com.campus.repair.vo.UserVO;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImageService {
    public record ImageFile(byte[] bytes, String contentType) {}
    private final RepairImageMapper images;
    private final RepairOrderMapper orders;
    private final OrderAccessService access;
    private final Clock clock;
    private final Path directory;

    public ImageService(RepairImageMapper images, RepairOrderMapper orders, OrderAccessService access,
            Clock clock, @Value("${app.upload.directory:./uploads}") String directory) {
        this.images = images; this.orders = orders; this.access = access; this.clock = clock;
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    public Map<String, String> upload(UserVO user, MultipartFile file) {
        if (user.role() == UserRole.ADMIN) throw new BusinessException(ErrorCode.FORBIDDEN);
        if (user.role() == UserRole.STUDENT) access.student(user);
        else access.requireAvailable(access.worker(user));
        var encoded = ImageCodec.encode(file);
        var image = new RepairImageEntity();
        image.setId(UUID.randomUUID().toString()); image.setOwnerId(user.id());
        image.setContentType(encoded.contentType());
        image.setCreateTime(LocalDateTime.ofInstant(clock.instant(), ZoneId.of("Asia/Shanghai")));
        Path path = directory.resolve(image.getId());
        try {
            Files.createDirectories(directory);
            Files.write(path, encoded.bytes(), StandardOpenOption.CREATE_NEW);
            try { images.insert(image); }
            catch (RuntimeException exception) { Files.deleteIfExists(path); throw exception; }
        } catch (IOException exception) { throw new BusinessException(ErrorCode.INTERNAL_ERROR); }
        return Map.of("url", "/api/images/" + image.getId());
    }

    /** Called within the order's transaction. A file can only bind once to its owner's order. */
    public String bind(UserVO user, String url, long orderId) {
        if (url == null || url.isBlank()) return null;
        if (!url.matches("/api/images/[0-9a-f-]{36}")) throw new BusinessException(ErrorCode.INVALID_REFERENCE);
        String id = url.substring("/api/images/".length());
        int changed = images.update(null, new LambdaUpdateWrapper<RepairImageEntity>()
                .eq(RepairImageEntity::getId, id).eq(RepairImageEntity::getOwnerId, user.id())
                .isNull(RepairImageEntity::getOrderId).set(RepairImageEntity::getOrderId, orderId));
        if (changed != 1) throw new BusinessException(ErrorCode.INVALID_REFERENCE);
        return url;
    }

    public ImageFile read(UserVO user, String id) {
        if (!id.matches("[0-9a-f-]{36}")) throw new BusinessException(ErrorCode.NOT_FOUND);
        var image = images.selectById(id);
        if (image == null) throw new BusinessException(ErrorCode.NOT_FOUND);
        if (!image.getOwnerId().equals(user.id())) {
            if (image.getOrderId() == null) throw new BusinessException(ErrorCode.NOT_FOUND);
            access.requireView(user, orders.selectById(image.getOrderId()));
        }
        try {
            Path path = directory.resolve(id);
            if (Files.isSymbolicLink(path) || !Files.isRegularFile(path)) throw new BusinessException(ErrorCode.NOT_FOUND);
            return new ImageFile(Files.readAllBytes(path), image.getContentType());
        } catch (IOException exception) { throw new BusinessException(ErrorCode.NOT_FOUND); }
    }
}
