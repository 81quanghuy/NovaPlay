package vn.iotstar.userservice.util;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.*;
import java.time.Instant;

@Getter @Setter
public abstract class AuditableDocument {
    @CreatedDate        private Instant createdAt;
    @LastModifiedDate   private Instant updatedAt;
    @CreatedBy          private String createdBy;
    @LastModifiedBy     private String updatedBy;
}