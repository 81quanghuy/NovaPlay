package vn.iotstar.userservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.userservice.model.entity.User;

@Repository
public interface IUserRepository extends JpaRepository<User, String> {

    User findByUsername(String userName);
    Boolean existsByAccountId(String accountId);
}
