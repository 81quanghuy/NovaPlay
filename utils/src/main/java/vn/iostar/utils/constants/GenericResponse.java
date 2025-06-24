package vn.iostar.utils.constants;

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
}
