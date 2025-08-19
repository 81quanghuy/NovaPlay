package vn.iotstar.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.userservice.model.entity.FavoriteItem;

import java.util.UUID;

@Repository
public interface IFavoriteItemRepository extends JpaRepository<FavoriteItem, UUID> {

}
