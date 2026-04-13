package gov.bf.ascelc.univers_audits.repository;

import gov.bf.ascelc.univers_audits.enums.TypeDeclarant;
import gov.bf.ascelc.univers_audits.model.entity.Declarant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;


@Repository
public interface DeclarantRepository
        extends JpaRepository<Declarant, UUID> {

    Optional<Declarant> findByEmail(String email);

    Optional<Declarant> findByPhoneNumber(String phoneNumber);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    Page<Declarant> findByTypeDeclarant(
            TypeDeclarant typeDeclarant, Pageable pageable);

    @Query("""
            SELECT d FROM Declarant d
            WHERE (:email IS NOT NULL AND d.email = :email)
            OR (:phone IS NOT NULL AND d.phoneNumber = :phone)
            """)
    List<Declarant> findByEmailOrPhone(
            @Param("email") String email,
            @Param("phone") String phone);

    @Query("""
            SELECT d.typeDeclarant, COUNT(d)
            FROM Declarant d
            GROUP BY d.typeDeclarant
            """)
    List<Object[]> countByType();
}