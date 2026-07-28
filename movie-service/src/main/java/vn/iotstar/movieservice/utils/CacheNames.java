package vn.iotstar.movieservice.utils;

/**
 * Tên các vùng cache. Khai báo tập trung vì {@code @Cacheable} và {@code @CacheEvict} phải dùng
 * cùng một chuỗi — gõ lệch một ký tự sẽ khiến cache không bao giờ bị xoá mà không báo lỗi gì.
 */
public final class CacheNames {

    private CacheNames() {}

    /** Chi tiết phim, khoá theo id hoặc slug. */
    public static final String MOVIE_DETAIL = "movie-detail";

    /** Toàn bộ danh sách thể loại; nhỏ và gần như không đổi. */
    public static final String GENRE_LIST = "genre-list";
}
