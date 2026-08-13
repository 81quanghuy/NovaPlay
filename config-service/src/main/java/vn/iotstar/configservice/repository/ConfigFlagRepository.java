package vn.iotstar.configservice.repository;

import org.springframework.data.mongodb.repository.MongoRepository;
import vn.iotstar.configservice.model.entity.ConfigFlag;

public interface ConfigFlagRepository extends MongoRepository<ConfigFlag, String> {
}
