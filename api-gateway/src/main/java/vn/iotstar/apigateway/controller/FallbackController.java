package vn.iotstar.apigateway.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import reactor.core.publisher.Mono;
import vn.iotstar.apigateway.constants.GenericResponse;

import static vn.iotstar.apigateway.constants.GateWayContants.CONTACT_SUPPORT_MESSAGE;

@RequestMapping
public class FallbackController {

    /**
     * This method is used to handle fallback for all services.
     * It returns a generic response indicating that the user should contact support.
     *
     * @return Mono<GenericResponse> - A reactive response containing the message and status code.
     */
    @RequestMapping("/contactSupport")
    public Mono<GenericResponse> contactSupport() {
        GenericResponse response = new GenericResponse();
        response.setMessage(CONTACT_SUPPORT_MESSAGE);
        response.setStatusCode(500);
        response.setSuccess(Boolean.TRUE);
        return Mono.just(response);
    }
}
