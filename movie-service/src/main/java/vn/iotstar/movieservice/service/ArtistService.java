package vn.iotstar.movieservice.service;

import org.springframework.data.domain.Pageable;
import vn.iotstar.movieservice.model.dto.ArtistDTO;
import vn.iotstar.movieservice.model.dto.ArtistRequest;
import vn.iotstar.movieservice.model.dto.PageResponse;

public interface ArtistService {

    PageResponse<ArtistDTO> search(String q, Pageable pageable);

    ArtistDTO findById(String id);

    ArtistDTO create(ArtistRequest request);

    ArtistDTO update(String id, ArtistRequest request);

    void delete(String id);
}
