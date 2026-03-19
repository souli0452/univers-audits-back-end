package gov.bf.ascelc.univers_audits.model.entity;
import gov.bf.ascelc.univers_audits.abstracts.AuditEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
@Getter
@Setter
@Entity
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@ToString(callSuper = true)
@Table(name = "declarant")
public class Declarant extends AuditEntity {

    private String firstName;
    private String lastName;
    private Boolean anonymous;
    private String phoneNumber;
    @Column(unique = true)
    private String email;
    private String profession;
    private String commune;
    private String address;


}


