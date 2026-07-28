package vn.iotstar.userservice.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.userservice.model.entity.FavoriteItem;

@Repository
public interface IFavoriteItemRepository extends MongoRepository<FavoriteItem, String> {

    Page<FavoriteItem> findByUserId(String userId, Pageable pageable);

    boolean existsByUserIdAndMovieId(String userId, String movieId);

    void deleteByUserIdAndMovieId(String userId, String movieId);

    /**
     * Trả về số bản ghi đã xoá. Kiểu trả về {@code long} khiến Spring Data phát sinh một
     * lệnh delete duy nhất thay vì nạp toàn bộ document rồi mới xoá từng cái.
     */
    long deleteAllByUserId(String userId);
}
