package gov.bf.ascelc.univers_audits.mapper;

import gov.bf.ascelc.univers_audits.model.dto.response.InvestigationMemberResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.InvestigationResponse;
import gov.bf.ascelc.univers_audits.model.dto.response.InvestigationSummaryResponse;
import gov.bf.ascelc.univers_audits.model.entity.Investigation;
import gov.bf.ascelc.univers_audits.model.entity.InvestigationMember;
import org.mapstruct.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.WARN,
        uses = { AgentMapper.class }
)
public interface InvestigationMapper {

    @Mapping(target = "dossierId",
            source = "dossier.id")
    @Mapping(target = "dossierNumber",
            source = "dossier.number")
    @Mapping(target = "dossierObject",
            source = "dossier.object")
    @Mapping(target = "overdue",       ignore = true)
    @Mapping(target = "remainingDays", ignore = true)
    InvestigationResponse toResponse(Investigation investigation);

    @AfterMapping
    default void fillCalculated(
            Investigation inv,
            @MappingTarget InvestigationResponse response) {
        response.setOverdue(inv.isOverdue());
        response.setRemainingDays(inv.getRemainingDays());
    }

    @Mapping(target = "overdue",       ignore = true)
    @Mapping(target = "remainingDays", ignore = true)
    @Mapping(target = "memberCount",   ignore = true)
    InvestigationSummaryResponse toSummaryResponse(
            Investigation investigation);

    @AfterMapping
    default void fillSummaryCalculated(
            Investigation inv,
            @MappingTarget InvestigationSummaryResponse response) {
        response.setOverdue(inv.isOverdue());
        response.setRemainingDays(inv.getRemainingDays());
        response.setMemberCount(
                inv.getMembers() != null
                        ? (int) inv.getMembers().stream()
                        .filter(m -> Boolean.TRUE.equals(
                                m.getActive()))
                        .count()
                        : 0);
    }

    @Mapping(target = "dateAttribution",
            source = "createdAt",
            qualifiedByName = "instantVersLocalDate")
    InvestigationMemberResponse toMemberResponse(
            InvestigationMember member);

    @Named("instantVersLocalDate")
    default LocalDate instantVersLocalDate(Instant instant) {
        if (instant == null) return null;
        return instant.atZone(ZoneId.of("Africa/Ouagadougou")).toLocalDate();
    }

    @AfterMapping
    default void fillMemberDate(
            InvestigationMember member,
            @MappingTarget InvestigationMemberResponse response) {
        if (member.getCreatedAt() != null) {
            response.setDateAttribution(
                    member.getCreatedAt()
                            .atZone(java.time.ZoneId.of(
                                    "Africa/Ouagadougou"))
                            .toLocalDate());
        }}
}