package vn.iotstar.userservice.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class GetWatchHistoryRequest {

    private String watchHistoryId;

    @NotBlank
    private String userId;

    @NotBlank
    private String movieId;

    private Long watchedAt; // timestamp in milliseconds
    private Long totalWatchTime; // total watch time in milliseconds
    private int pageNumber;
    private int pageSize;
}
