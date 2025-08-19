package vn.iotstar.apigateway.configs;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class SecurityConfigItem {

    private String pathPattern;
    private boolean authRequired;
    private boolean userAllowed;
    private boolean adminAllowed;
}
