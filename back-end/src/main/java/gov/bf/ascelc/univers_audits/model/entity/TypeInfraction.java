package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "type_infraction", indexes = {
        @Index(name = "idx_type_infraction_code",
                columnList = "code", unique = true)
})
public class TypeInfraction extends AuditEntity {

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "libelle", nullable = false, length = 300)
    private String libelle;

    @Column(name = "article_code_penal", length = 100)
    private String articleCodePenal;

    @Column(name = "article_loi_004", length = 100)
    private String articleLoi004;

    @Column(name = "implique_ddip", nullable = false)
    @Builder.Default
    private Boolean impliqueDdip = false;

    @Column(name = "actif", nullable = false)
    @Builder.Default
    private Boolean actif = true;

    @Column(name = "ordre")
    @Builder.Default
    private Integer ordre = 0;
}
