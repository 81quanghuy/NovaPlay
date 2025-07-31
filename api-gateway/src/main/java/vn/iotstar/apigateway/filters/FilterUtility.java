package vn.iotstar.apigateway.filters;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.util.List;

import static vn.iotstar.apigateway.constants.GateWayContants.CORRELATION_ID;

@Component
public class FilterUtility {

    public String getCorrelationId(HttpHeaders requestHeaders) {
        List<String> requestHeaderList = requestHeaders.get(CORRELATION_ID);
        if (requestHeaderList != null) {
            return requestHeaderList.stream().findFirst().orElse(null);
        } else {
            return null;
        }
    }

    public ServerWebExchange setRequestHeader(ServerWebExchange exchange, String name, String value) {
        return exchange.mutate().request(exchange.getRequest().mutate().header(name, value).build()).build();
    }

    public ServerWebExchange setCorrelationId(ServerWebExchange exchange, String correlationId) {
        return this.setRequestHeader(exchange, CORRELATION_ID, correlationId);
    }

}
