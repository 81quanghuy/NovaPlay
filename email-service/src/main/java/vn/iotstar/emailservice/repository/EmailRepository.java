package vn.iotstar.emailservice.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import vn.iotstar.emailservice.model.entity.Email;


@Repository
public interface EmailRepository extends JpaRepository<Email, String> {

    Email findByEmail(String email);


}
