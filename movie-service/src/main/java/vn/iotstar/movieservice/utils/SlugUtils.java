package vn.iotstar.movieservice.utils;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Sinh slug thân thiện URL từ tiêu đề.
 * <p>
 * Tiêu đề phim ở đây phần lớn là tiếng Việt, nên phải bóc dấu trước khi bỏ ký tự không phải ASCII —
 * nếu không "Người Nhện" sẽ ra slug rỗng thay vì "nguoi-nhen".
 */
public final class SlugUtils {

    private SlugUtils() {}

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_DASHES = Pattern.compile("(^-+)|(-+$)");

    public static String toSlug(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        // Đ/đ không phải là chữ cái có dấu phụ trong Unicode nên NFD không tách được; phải thay tay.
        String replaced = input.replace('Đ', 'D').replace('đ', 'd');

        String withoutMarks = COMBINING_MARKS
                .matcher(Normalizer.normalize(replaced, Normalizer.Form.NFD))
                .replaceAll("");

        String slug = EDGE_DASHES.matcher(
                        NON_ALPHANUMERIC.matcher(withoutMarks.toLowerCase(Locale.ROOT)).replaceAll("-"))
                .replaceAll("");

        return slug.isEmpty() ? null : slug;
    }
}
