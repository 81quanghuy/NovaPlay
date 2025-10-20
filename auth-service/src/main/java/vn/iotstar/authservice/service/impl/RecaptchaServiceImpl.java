//package vn.iotstar.authservice.service.impl;
//
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//import org.springframework.util.LinkedMultiValueMap;
//import org.springframework.util.MultiValueMap;
//import org.springframework.web.client.RestTemplate;
//import vn.iotstar.authservice.model.dto.RecaptchaResponse;
//import vn.iotstar.authservice.service.RecaptchaService;
//
//@Service
//@Slf4j
//@RequiredArgsConstructor
//public class RecaptchaServiceImpl implements RecaptchaService {
//
//    private static final String GOOGLE_RECAPTCHA_VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";
//
//    @Value("${google.recaptcha.secret-key}")
//    private String recaptchaSecretKey;
//
//    private final RestTemplate restTemplate;
//
//    @Override
//    public boolean validateCaptcha(String captchaResponse, String clientIp) {
//        if (captchaResponse == null || captchaResponse.isEmpty()) {
//            return false;
//        }
//
//        MultiValueMap<String, String> requestMap = new LinkedMultiValueMap<>();
//        requestMap.add("secret", recaptchaSecretKey);
//        requestMap.add("response", captchaResponse);
//        requestMap.add("remoteip", clientIp);
//
//        try {
//            RecaptchaResponse apiResponse = restTemplate.postForObject(GOOGLE_RECAPTCHA_VERIFY_URL, requestMap, RecaptchaResponse.class);
//            if (apiResponse == null) {
//                log.error("reCAPTCHA validation returned null response.");
//                return false;
//            }
//            if (!apiResponse.isSuccess()) {
//                log.warn("reCAPTCHA validation failed with error codes: {}", apiResponse.getErrorCodes());
//            }
//            return apiResponse.isSuccess();
//        } catch (Exception e) {
//            log.error("Error during reCAPTCHA validation", e);
//            return false;
//        }
//    }
//}