package vn.iotstar.userservice.mapper;

import vn.iotstar.userservice.model.dto.FavoriteItemDTO;
import vn.iotstar.userservice.model.entity.FavoriteItem;

public class FavoriteItemMapper {

    private FavoriteItemMapper() {}

    public static FavoriteItemDTO toDto(FavoriteItem item) {
        return new FavoriteItemDTO(
                item.getMovieId(),
                item.getContentType(),
                item.getCreatedAt()
        );
    }
}
