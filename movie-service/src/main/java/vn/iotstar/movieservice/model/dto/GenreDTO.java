package vn.iotstar.movieservice.model.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

@Schema(name = "GenreDTO", description = "Một thể loại phim.")
public record GenreDTO(

        @Schema(description = "Mã thể loại.", example = "9f1c2a3e-...")
        String id,

        @Schema(description = "Tên hiển thị.", example = "Hành động")
        String name,

        @Schema(description = "Định danh thân thiện URL.", example = "hanh-dong")
        String slug

) implements Serializable {}
