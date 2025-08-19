package vn.iotstar.utils;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.io.Serializable;
import java.util.Date;

import static vn.iotstar.utils.constants.AppConst.*;

@MappedSuperclass
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Setter
@EntityListeners(AuditingEntityListener.class)
public abstract class AbstractBaseEntity implements Serializable {

    @CreatedDate
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Column(name = CREATED_AT, updatable = false)
    private Date createdAt;

    @CreatedBy
    @Column(name = CREATED_BY, updatable = false)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private String createdBy;

    @LastModifiedDate
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @Column(name = UPDATED_AT, insertable = false)
    private Date updatedAt;

    @LastModifiedBy
    @Column(name = UPDATED_BY, insertable = false)
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private String updatedBy;
}










