package com.civic.action.util;

public class MaskingUtil {

    public static String maskMobileNumber(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String cleaned = value.trim();
        // Match digits optionally starting with '+'
        if (cleaned.matches("^\\+?\\d+$")) {
            if (cleaned.length() <= 3) {
                return cleaned;
            }
            int maskLength = cleaned.length() - 3;
            StringBuilder sb = new StringBuilder();
            int startIdx = 0;
            if (cleaned.startsWith("+")) {
                sb.append("+");
                startIdx = 1;
            }
            for (int i = startIdx; i < maskLength; i++) {
                sb.append('*');
            }
            sb.append(cleaned.substring(maskLength));
            return sb.toString();
        }
        return value;
    }
}
