package vn.iotstar.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.userservice.model.entity.WatchProgress;

import java.util.UUID;

@Repository
public interface IWatchProgressRepository extends JpaRepository<WatchProgress, UUID> {


}
