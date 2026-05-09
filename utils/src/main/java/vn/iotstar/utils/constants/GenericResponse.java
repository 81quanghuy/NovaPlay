package vn.iotstar.utils.constants;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(
        description = "Generic response structure for API responses",
        title = "GenericResponse"
)
public class GenericResponse {
    private Boolean success;

    private String message;

    private Object result;

    private int statusCode;

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
