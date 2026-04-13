package gov.bf.ascelc.univers_audits.abstracts;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class AuditEntityDto {

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private UUID id;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Instant createdAt;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Instant updatedAt;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String createdById;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String updatedById;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private Long version;
}