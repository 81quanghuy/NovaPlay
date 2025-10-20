package vn.iotstar.apigateway.filters;

import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import reactor.core.publisher.Mono;

@Configuration
public class ResponseTimeHeaderFilter {

    /**
     * A GlobalFilter that measures request duration and adds a 'X-Response-Time' header.
     */
    @Order(-1)
    @Bean
    public GlobalFilter responseTimeFilter() {
        return (exchange, chain) -> {
            long startTime = System.currentTimeMillis();

            return chain.filter(exchange).then(
                    Mono.fromRunnable(() -> {
                        long duration = System.currentTimeMillis() - startTime;
                        exchange.getResponse()
                                .getHeaders()
                                .add("X-Response-Time", duration + "ms");
                    })
            );
        };
    }
}
