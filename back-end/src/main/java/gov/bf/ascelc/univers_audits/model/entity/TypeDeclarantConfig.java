package gov.bf.ascelc.univers_audits.model.entity;

import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import gov.bf.ascelc.univers_audits.enums.TypeDeclarant;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;


@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "type_declarant_config", indexes = {

        @Index(name = "idx_type_declarant_config",
                columnList = "type_declarant", unique = true)
})
public class TypeDeclarantConfig extends AuditEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "type_declarant", nullable = false,
            unique = true, length = 30)
    private TypeDeclarant typeDeclarant;

    @Column(name = "label", nullable = false, length = 150)
    private String label;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "visible_on_public_form", nullable = false)
    @Builder.Default
    private Boolean visibleOnPublicForm = true;

    @Column(name = "id_document_required", nullable = false)
    @Builder.Default
    private Boolean idDocumentRequired = false;


    @Column(name = "protection_available", nullable = false)
    @Builder.Default
    private Boolean protectionAvailable = false;

    @Column(name = "consent_required", nullable = false)
    @Builder.Default
    private Boolean consentRequired = true;

    @Column(name = "display_order")
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(name = "active", nullable = false)
    @Builder.Default
    private Boolean active = true;
}