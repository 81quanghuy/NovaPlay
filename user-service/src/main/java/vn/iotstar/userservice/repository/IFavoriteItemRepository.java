package vn.iotstar.userservice.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.userservice.model.entity.FavoriteItem;

@Repository
public interface IFavoriteItemRepository extends MongoRepository<FavoriteItem, String> {

}
