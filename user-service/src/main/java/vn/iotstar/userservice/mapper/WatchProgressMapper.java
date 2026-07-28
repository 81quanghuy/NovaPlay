package vn.iotstar.userservice.mapper;

import vn.iotstar.userservice.model.dto.WatchProgressDTO;
import vn.iotstar.userservice.model.entity.WatchProgress;

public class WatchProgressMapper {

    private WatchProgressMapper() {}

    public static WatchProgressDTO toDto(WatchProgress progress) {
        return new WatchProgressDTO(
                progress.getMovieId(),
                progress.getSeriesId(),
                progress.getSeasonId(),
                progress.getEpisodeId(),
                progress.getPositionMs(),
                progress.getDurationMs(),
                progress.getPercent(),
                progress.isCompleted(),
                progress.getLastWatchedAt()
        );
    }
}
