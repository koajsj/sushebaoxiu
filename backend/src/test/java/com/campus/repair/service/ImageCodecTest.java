package com.campus.repair.service;

import static org.junit.jupiter.api.Assertions.*;
import com.campus.repair.common.BusinessException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ImageCodecTest {
    private byte[] png() throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }
    @Test void validImageIsReencoded() throws Exception {
        var image = ImageCodec.encode(new MockMultipartFile("file", "../../x.png", "image/png", png()));
        assertEquals("image/png", image.contentType());
        assertNotNull(ImageIO.read(new java.io.ByteArrayInputStream(image.bytes())));
    }
    @Test void validJpegIsSupported() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"jpeg",output);
        var encoded=ImageCodec.encode(new MockMultipartFile("file","x.jpg","image/jpeg",output.toByteArray()));
        assertEquals("image/jpeg",encoded.contentType());
        assertNotNull(ImageIO.read(new java.io.ByteArrayInputStream(encoded.bytes())));
    }
    @Test void fakeImageIsRejected() {
        assertThrows(BusinessException.class, () -> ImageCodec.encode(new MockMultipartFile("file", "x.png", "image/png", "<script>".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    }
    @Test void mimeMismatchIsRejected() throws Exception {
        assertThrows(BusinessException.class, () -> ImageCodec.encode(new MockMultipartFile("file", "x.jpg", "image/jpeg", png())));
    }
    @Test void oversizedImageIsRejected() {
        assertThrows(BusinessException.class, () -> ImageCodec.encode(new MockMultipartFile("file", "x.png", "image/png", new byte[5*1024*1024+1])));
    }
    @Test void excessivePixelDimensionsAreRejectedBeforeDecode() throws Exception {
        byte[] input = png();
        java.nio.ByteBuffer.wrap(input,16,8).putInt(5000).putInt(5000);
        var crc = new java.util.zip.CRC32(); crc.update(input,12,17);
        java.nio.ByteBuffer.wrap(input,29,4).putInt((int) crc.getValue());
        assertThrows(BusinessException.class, () -> ImageCodec.encode(new MockMultipartFile("file", "huge.png", "image/png", input)));
    }
    @Test void trailingPayloadIsRemoved() throws Exception {
        var raw = new ByteArrayOutputStream(); raw.write(png());
        raw.write("SENTINEL_TRAILING_PAYLOAD".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var encoded = ImageCodec.encode(new MockMultipartFile("file", "x.png", "image/png", raw.toByteArray()));
        assertFalse(new String(encoded.bytes(), java.nio.charset.StandardCharsets.ISO_8859_1).contains("SENTINEL_TRAILING_PAYLOAD"));
    }
    @Test void emptyImageIsRejected() {
        assertThrows(BusinessException.class, () -> ImageCodec.encode(new MockMultipartFile("file", new byte[0])));
    }
}
