package vn.iotstar.movieservice.service;

import vn.iotstar.movieservice.model.dto.GenreDTO;
import vn.iotstar.movieservice.model.dto.GenreRequest;

import java.util.List;

public interface GenreService {

    List<GenreDTO> findAll();

    GenreDTO findById(String id);

    GenreDTO create(GenreRequest request);

    GenreDTO update(String id, GenreRequest request);

    void delete(String id);
}
