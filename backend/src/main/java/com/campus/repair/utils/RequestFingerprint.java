package com.campus.repair.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Length-prefixing keeps distinct field sequences from sharing a digest input. */
public final class RequestFingerprint {
    private RequestFingerprint() {}
    public static String of(String... fields) {
        try {
            var digest=MessageDigest.getInstance("SHA-256");
            for(String field:fields) {
                byte[] bytes=(field==null?"":field).getBytes(StandardCharsets.UTF_8);
                digest.update(java.nio.ByteBuffer.allocate(4).putInt(bytes.length).array());
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
