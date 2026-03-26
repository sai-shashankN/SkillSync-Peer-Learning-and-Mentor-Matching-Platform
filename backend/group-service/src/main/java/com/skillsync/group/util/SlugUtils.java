package com.skillsync.group.util;

public final class SlugUtils {

    private SlugUtils() {
    }

    public static String slugify(String input) {
        String normalized = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "");
        return normalized.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }
}
