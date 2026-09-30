package com.campus.repair.service;

import com.campus.repair.common.BusinessException;
import com.campus.repair.common.ErrorCode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.springframework.web.multipart.MultipartFile;

/** Decodes only bounded raster images and strips metadata/payload by re-encoding. */
public final class ImageCodec {
    public record Encoded(byte[] bytes, String contentType) {}
    private ImageCodec() {}

    public static Encoded encode(MultipartFile file) {
        if (file.isEmpty() || file.getSize() > 5L * 1024 * 1024)
            throw new BusinessException(ErrorCode.INVALID_IMAGE);
        String expected = switch (file.getContentType() == null ? "" : file.getContentType()) {
            case "image/png" -> "png";
            case "image/jpeg" -> "jpeg";
            default -> throw new BusinessException(ErrorCode.INVALID_IMAGE);
        };
        try (var input = file.getInputStream(); var stream = ImageIO.createImageInputStream(input)) {
            if (stream == null) throw new BusinessException(ErrorCode.INVALID_IMAGE);
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw new BusinessException(ErrorCode.INVALID_IMAGE);
            var reader = readers.next();
            try {
                if (!reader.getFormatName().equalsIgnoreCase(expected)) throw new BusinessException(ErrorCode.INVALID_IMAGE);
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > 20_000_000)
                    throw new BusinessException(ErrorCode.INVALID_IMAGE);
                var image = reader.read(0);
                var output = new ByteArrayOutputStream();
                if (!ImageIO.write(image, expected, output)) throw new BusinessException(ErrorCode.INVALID_IMAGE);
                return new Encoded(output.toByteArray(), file.getContentType());
            } finally { reader.dispose(); }
        } catch (IOException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE);
        }
    }
}
