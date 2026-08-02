package vn.iotstar.apigateway.configs;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;
import vn.iotstar.apigateway.constants.GateWayConstants;

import java.net.InetSocketAddress;

@Configuration
public class RouteConfig {

    @Bean
    public KeyResolver userEmailOrIpKeyResolver() {
        return exchange -> {
            String userEmail = exchange.getRequest().getHeaders().getFirst(GateWayConstants.X_USER_EMAIL);
            if (userEmail != null && !userEmail.isEmpty()) {
                return Mono.just(userEmail);
            }
            InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
            if (remoteAddress == null) {
                return Mono.just("unknown");
            }
            return Mono.just(remoteAddress.getAddress().getHostAddress());
        };
    }
}
