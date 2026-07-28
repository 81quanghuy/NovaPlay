package vn.iotstar.movieservice.mapper;

import org.springframework.stereotype.Component;
import vn.iotstar.movieservice.model.dto.*;
import vn.iotstar.movieservice.model.entity.Artist;
import vn.iotstar.movieservice.model.entity.Genre;
import vn.iotstar.movieservice.model.entity.Movie;
import vn.iotstar.movieservice.model.entity.embedded.CastMember;
import vn.iotstar.movieservice.model.entity.embedded.Episode;
import vn.iotstar.movieservice.model.entity.embedded.GenreRef;

import java.util.List;

/**
 * Chuyển document sang DTO.
 * <p>
 * Controller không bao giờ trả thẳng entity: làm vậy sẽ lộ {@code version} và các field audit ra
 * ngoài, đồng thời khiến mọi lần đổi tên field trong document trở thành một breaking change của API.
 */
@Component
public class MovieMapper {

    public MovieSummaryDTO toSummary(Movie movie) {
        return new MovieSummaryDTO(
                movie.getId(),
                movie.getSlug(),
                movie.getTitle(),
                movie.getReleaseDate(),
                movie.getDurationInMinutes(),
                movie.getPosterUrl(),
                movie.isSeries(),
                movie.getStatus(),
                toGenreDTOs(movie.getGenres())
        );
    }

    public MovieDetailDTO toDetail(Movie movie) {
        return new MovieDetailDTO(
                movie.getId(),
                movie.getSlug(),
                movie.getTitle(),
                movie.getDescription(),
                movie.getReleaseDate(),
                movie.getDurationInMinutes(),
                movie.getPosterUrl(),
                movie.isSeries(),
                movie.getStatus(),
                toGenreDTOs(movie.getGenres()),
                toCastDTOs(movie.getCast()),
                toEpisodeDTOs(movie.getEpisodes())
        );
    }

    public GenreDTO toDTO(Genre genre) {
        return new GenreDTO(genre.getId(), genre.getName(), genre.getSlug());
    }

    public ArtistDTO toDTO(Artist artist) {
        return new ArtistDTO(artist.getId(), artist.getFullName(), artist.getBio(),
                artist.getAvatarUrl());
    }

    /**
     * {@code GenreRef} nhúng trong phim không mang slug, nên slug ở đây là null. Giao diện danh
     * sách chỉ cần id và tên; ai cần slug thì gọi endpoint thể loại.
     */
    private List<GenreDTO> toGenreDTOs(List<GenreRef> refs) {
        if (refs == null) return List.of();
        return refs.stream().map(r -> new GenreDTO(r.getId(), r.getName(), null)).toList();
    }

    private List<CastMemberDTO> toCastDTOs(List<CastMember> cast) {
        if (cast == null) return List.of();
        return cast.stream()
                .map(c -> new CastMemberDTO(c.getArtistId(), c.getFullName(), c.getRole(),
                        c.getCharacterName()))
                .toList();
    }

    private List<EpisodeDTO> toEpisodeDTOs(List<Episode> episodes) {
        if (episodes == null) return List.of();
        return episodes.stream()
                .map(e -> new EpisodeDTO(e.getEpisodeNumber(), e.getTitle(), e.getVideoUrl(),
                        e.getDurationInMinutes()))
                .toList();
    }
}
