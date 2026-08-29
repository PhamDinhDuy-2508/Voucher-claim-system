package com.example.voucherclaim.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class RequestIds {
    private RequestIds() {
    }

    /** Builds the stable operation ID from the business key that permits one claim. */
    public static String forClaim(String campaignId, String userId) {
        String source = campaignId + ":" + userId;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

}
