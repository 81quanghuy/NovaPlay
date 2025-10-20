package vn.iotstar.authservice.model.dto;

public record VerifyOtpRequest(String email, String otp) {}