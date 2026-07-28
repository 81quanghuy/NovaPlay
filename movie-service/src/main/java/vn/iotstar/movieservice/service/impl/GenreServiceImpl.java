package vn.iotstar.movieservice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import vn.iotstar.movieservice.mapper.MovieMapper;
import vn.iotstar.movieservice.model.dto.GenreDTO;
import vn.iotstar.movieservice.model.dto.GenreRequest;
import vn.iotstar.movieservice.model.entity.Genre;
import vn.iotstar.movieservice.repository.GenreRepository;
import vn.iotstar.movieservice.repository.MovieRepository;
import vn.iotstar.movieservice.service.GenreService;
import vn.iotstar.movieservice.utils.CacheNames;
import vn.iotstar.movieservice.utils.SlugUtils;
import vn.iotstar.utils.exceptions.wrapper.BadRequestException;
import vn.iotstar.utils.exceptions.wrapper.ResourceNotFoundException;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class GenreServiceImpl implements GenreService {

    private final GenreRepository genreRepository;
    private final MovieRepository movieRepository;
    private final MovieMapper mapper;

    @Override
    @Cacheable(value = CacheNames.GENRE_LIST, key = "'all'")
    public List<GenreDTO> findAll() {
        return genreRepository.findAllByOrderByNameAsc().stream().map(mapper::toDTO).toList();
    }

    @Override
    public GenreDTO findById(String id) {
        return mapper.toDTO(getOrThrow(id));
    }

    @Override
    @CacheEvict(value = CacheNames.GENRE_LIST, allEntries = true)
    public GenreDTO create(GenreRequest request) {
        Genre genre = Genre.builder()
                .id(UUID.randomUUID().toString())
                .name(request.name())
                .slug(requireSlug(request.name()))
                .build();
        genre.normalize();

        try {
            return mapper.toDTO(genreRepository.save(genre));
        } catch (DuplicateKeyException e) {
            // Dựa vào unique index thay vì kiểm tra trước rồi mới ghi: kiểm tra trước có khoảng
            // trống giữa hai bước, hai request đồng thời vẫn lọt qua được.
            throw new BadRequestException("Thể loại đã tồn tại: " + request.name());
        }
    }

    @Override
    @CacheEvict(value = {CacheNames.GENRE_LIST, CacheNames.MOVIE_DETAIL}, allEntries = true)
    public GenreDTO update(String id, GenreRequest request) {
        Genre genre = getOrThrow(id);
        String oldName = genre.getName();

        genre.setName(request.name());
        genre.setSlug(requireSlug(request.name()));
        genre.normalize();

        Genre saved;
        try {
            saved = genreRepository.save(genre);
        } catch (DuplicateKeyException e) {
            throw new BadRequestException("Thể loại đã tồn tại: " + request.name());
        }

        // Tên thể loại được nhúng vào từng phim để đọc không phải join, nên đổi tên ở đây phải
        // lan sang mọi phim đang tham chiếu, nếu không catalog sẽ hiển thị tên cũ mãi mãi.
        if (!saved.getName().equals(oldName)) {
            long affected = movieRepository.renameEmbeddedGenre(id, saved.getName());
            log.info("Renamed genre {} from '{}' to '{}', updated {} movies",
                    id, oldName, saved.getName(), affected);
        }

        return mapper.toDTO(saved);
    }

    @Override
    @CacheEvict(value = {CacheNames.GENRE_LIST, CacheNames.MOVIE_DETAIL}, allEntries = true)
    public void delete(String id) {
        Genre genre = getOrThrow(id);

        // Chặn xoá thay vì âm thầm gỡ khỏi phim: mất thể loại của hàng loạt phim là thao tác
        // không hoàn tác được, admin phải chủ động gỡ khỏi từng phim trước.
        if (movieRepository.existsByGenresId(id)) {
            throw new BadRequestException(
                    "Không thể xoá thể loại đang được phim sử dụng: " + genre.getName());
        }

        genreRepository.delete(genre);
        log.info("Deleted genre {} ({})", id, genre.getName());
    }

    private Genre getOrThrow(String id) {
        return genreRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thể loại: " + id));
    }

    private String requireSlug(String name) {
        String slug = SlugUtils.toSlug(name);
        if (slug == null) {
            throw new BadRequestException("Tên thể loại phải chứa ít nhất một chữ hoặc số: " + name);
        }
        return slug;
    }
}
