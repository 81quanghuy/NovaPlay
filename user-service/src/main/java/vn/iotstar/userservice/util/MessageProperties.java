package vn.iotstar.userservice.util;

public class MessageProperties {

    public static final String USERNAME_NOT_BLANK = "Username không được để trống";
    public static final String USERNAME_SIZE = "Username phải có từ 4 đến 20 ký tự";

    public static final String EMAIL_NOT_BLANK = "Email không được để trống";
    public static final String EMAIL_SIZE = "Email phải có từ 5 đến 50 ký tự";
    public static final String EMAIL_INVALID = "Email không hợp lệ";

    public static final String USER_CREATE_SUCCESS = "Tạo tài khoản thành công";
    public static final String USER_NOT_FOUND = "Không tìm thấy người dùng với id: %s";
    public static final String USER_UPDATE_SUCCESS = "Cập nhật thông tin người dùng thành công";

    //message for user
    public static final String USER_GET_SUCCESS = "Lấy thông tin người dùng thành công";
    public static final String USER_DELETE_SUCCESS = "Xóa người dùng thành công";
    public static final String USER_ALREADY_EXISTS = "Người dùng đã tồn tại với email đã chỉ định";
    // Message Watch History
    public static final String WATCH_HISTORY_CREATE_SUCCESS = "Lịch sử xem đã được tạo thành công";
    public static final String WATCH_HISTORY_NOT_FOUND = "Không tìm thấy lịch sử xem cho người dùng và phim đã chỉ định";
    public static final String WATCH_HISTORY_RETRIEVE_SUCCESS = "Lịch sử xem đã được lấy thành công";
    public static final String WATCH_HISTORY_DELETE_SUCCESS = "Lịch sử xem đã được xóa thành công";
    public static final String WATCH_HISTORY_ALREADY_EXISTS = "Lịch sử xem đã tồn tại cho người dùng và phim đã chỉ định";

    // Message Favorite Movies
    public static final String FAVORITE_MOVIE_CREATE_SUCCESS = "Phim yêu thích đã được thêm thành công";
    public static final String FAVORITE_MOVIE_NOT_FOUND = "Không tìm thấy phim yêu thích cho người dùng và phim đã chỉ định";
    public static final String FAVORITE_MOVIE_RETRIEVE_ALL_SUCCESS = "Lấy tất cả phim yêu thích thành công";
    public static final String FAVORITE_MOVIE_DELETE_SUCCESS = "Phim yêu thích đã được xóa thành công";
    public static final String FAVORITE_MOVIE_ALREADY_EXISTS = "Phim yêu thích đã tồn tại cho người dùng và phim đã chỉ định";
    public static final String FAVORITE_MOVIE_DELETE_ALL_SUCCESS = "Đã xóa tất cả phim yêu thích của người dùng thành công";

    public static final String PASSWORD_NOT_BLANK = "Password không được để trống";
    public static final String PASSWORD_SIZE = "Password phải có ít nhất 6 ký tự";
    public static final String PASSWORD_COMPLEXITY = "Password phải chứa ít nhất một chữ hoa, một chữ thường và một chữ số";

    public static final String ACCOUNT_NOT_FOUND = "Tài khoản không tồn tại ";
    public static final String INVALID_PASSWORD = "Mật khẩu không hợp lệ";
    public static final String ACCOUNT_INACTIVE = "Tài khoản đã bị vô hiệu hóa";
    public static final String LOGIN_SUCCESS = "Đăng nhập thành công";
    public static final String EMAIL_ALREADY_EXISTS = "Email đã tồn tại";
    public static final String SEND_OTP_SUCCESS = "Gửi mã OTP thành công";
}
