package vn.iotstar.movieservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import vn.iotstar.movieservice.mapper.MovieMapper;
import vn.iotstar.movieservice.model.dto.ArtistDTO;
import vn.iotstar.movieservice.model.dto.ArtistRequest;
import vn.iotstar.movieservice.model.dto.PageResponse;
import vn.iotstar.movieservice.model.entity.Artist;
import vn.iotstar.movieservice.repository.ArtistRepository;
import vn.iotstar.movieservice.repository.MovieRepository;
import vn.iotstar.movieservice.service.ArtistService;
import vn.iotstar.movieservice.utils.CacheNames;
import vn.iotstar.movieservice.exception.BadRequestException;
import vn.iotstar.movieservice.exception.ResourceNotFoundException;

import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class ArtistServiceImpl implements ArtistService {

    private final ArtistRepository artistRepository;
    private final MovieRepository movieRepository;
    private final MovieMapper mapper;

    @Override
    public PageResponse<ArtistDTO> search(String q, Pageable pageable) {
        Page<Artist> page = (q == null || q.isBlank())
                ? artistRepository.findAll(pageable)
                : artistRepository.findByFullNameContainingIgnoreCase(q.trim(), pageable);
        return PageResponse.from(page, mapper::toDTO);
    }

    @Override
    public ArtistDTO findById(String id) {
        return mapper.toDTO(getOrThrow(id));
    }

    @Override
    public ArtistDTO create(ArtistRequest request) {
        Artist artist = Artist.builder()
                .id(UUID.randomUUID().toString())
                .fullName(request.fullName())
                .bio(request.bio())
                .avatarUrl(request.avatarUrl())
                .build();
        artist.normalize();
        return mapper.toDTO(artistRepository.save(artist));
    }

    @Override
    @CacheEvict(value = CacheNames.MOVIE_DETAIL, allEntries = true)
    public ArtistDTO update(String id, ArtistRequest request) {
        Artist artist = getOrThrow(id);
        String oldName = artist.getFullName();

        artist.setFullName(request.fullName());
        artist.setBio(request.bio());
        artist.setAvatarUrl(request.avatarUrl());
        artist.normalize();

        Artist saved = artistRepository.save(artist);

        // Tên nghệ sĩ được nhúng vào danh sách ê-kíp của từng phim, nên đổi tên phải lan sang mọi
        // phim liên quan, nếu không chi tiết phim sẽ hiện tên cũ mãi mãi.
        if (!saved.getFullName().equals(oldName)) {
            long affected = movieRepository.renameEmbeddedArtist(id, saved.getFullName());
            log.info("Renamed artist {} from '{}' to '{}', updated {} movies",
                    id, oldName, saved.getFullName(), affected);
        }

        return mapper.toDTO(saved);
    }

    @Override
    public void delete(String id) {
        Artist artist = getOrThrow(id);

        // Chặn xoá thay vì âm thầm gỡ khỏi ê-kíp: mất thông tin diễn viên của hàng loạt phim là
        // thao tác không hoàn tác được.
        if (movieRepository.existsByCastArtistId(id)) {
            throw new BadRequestException(
                    "Không thể xoá nghệ sĩ đang xuất hiện trong phim: " + artist.getFullName());
        }

        artistRepository.delete(artist);
        log.info("Deleted artist {} ({})", id, artist.getFullName());
    }

    private Artist getOrThrow(String id) {
        return artistRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy nghệ sĩ: " + id));
    }
}
