package vn.iotstar.authservice.service;

import java.util.Date;

public interface TokenBlacklist {

    void revoke(String jti, Date expiration);

    boolean isRevoked(String jti);
}
