package vn.iotstar.movieservice.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(
        description = "Generic response structure for API responses",
        title = "GenericResponse"
)
public class GenericResponse {
    private Boolean success;

    private String message;

    private Object result;

    private int statusCode;

    /** Chỉ được điền trên response lỗi, để đối chiếu với log. */
    private Instant timestamp;

    /** Chỉ được điền trên response lỗi, là request URI đã gây lỗi. */
    private String path;

    public static GenericResponse success(Object data, String message, int statusCode) {
        return GenericResponse.builder()
                .success(true)
                .message(message)
                .result(data)
                .statusCode(statusCode)
                .build();
    }

    public static GenericResponse success(Object data, String message) {
        return success(data, message, 200);
    }

    public static GenericResponse success(Object data) {
        return success(data, "Success", 200);
    }

    public static GenericResponse ok(String message) {
        return GenericResponse.builder()
                .success(true)
                .message(message)
                .statusCode(200)
                .build();
    }
}
