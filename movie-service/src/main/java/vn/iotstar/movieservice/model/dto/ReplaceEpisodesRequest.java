package vn.iotstar.movieservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Bọc danh sách tập trong một object thay vì nhận thẳng mảng JSON.
 * <p>
 * {@code @Valid} trên tham số kiểu {@code List} không cascade xuống từng phần tử trong Spring MVC,
 * nên nhận mảng trần sẽ khiến validation của {@link EpisodeRequest} không bao giờ chạy.
 */
@Schema(name = "ReplaceEpisodesRequest", description = "Thay toàn bộ danh sách tập của phim bộ.")
public record ReplaceEpisodesRequest(

        @Schema(description = "Danh sách tập mới. Gửi mảng rỗng để xoá hết tập.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        @Size(max = 2000, message = "Một phim bộ không được quá 2000 tập")
        List<@Valid EpisodeRequest> episodes
) {}
