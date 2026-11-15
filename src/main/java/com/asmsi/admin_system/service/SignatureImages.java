package com.asmsi.admin_system.service;

import java.util.Base64;

/** Checks a signature drawn on a signature pad, sent as a PNG data URL. */
public final class SignatureImages {
    private SignatureImages() {}

    private static final String PREFIX = "data:image/png;base64,";

    /** Decodes a PNG data URL; refuses anything that isn't a reasonably sized PNG. */
    public static byte[] decodePng(String dataUrl) {
        if (dataUrl == null || !dataUrl.startsWith(PREFIX)) throw new IllegalArgumentException("Draw your signature in the box first, or upload a picture of it.");
        byte[] b;
        try { b = Base64.getDecoder().decode(dataUrl.substring(PREFIX.length())); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("The signature could not be read. Clear it and draw it again."); }
        if (b.length < 200) throw new IllegalArgumentException("The signature box is empty. Draw your signature first.");
        if (b.length > 800_000) throw new IllegalArgumentException("The signature is too large.");
        if (b[0] != (byte) 0x89 || b[1] != 'P' || b[2] != 'N' || b[3] != 'G') throw new IllegalArgumentException("The signature is not a PNG picture.");
        return b;
    }
}
