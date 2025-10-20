package vn.iotstar.userservice.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.userservice.model.entity.WatchProgress;

@Repository
public interface IWatchProgressRepository extends MongoRepository<WatchProgress, String> {

}
