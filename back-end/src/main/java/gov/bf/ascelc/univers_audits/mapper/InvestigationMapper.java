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
import java.util.Collections;
import java.util.List;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.WARN,
        uses = { AgentMapper.class }
)
public interface InvestigationMapper {

    @Mapping(target = "dossierId",     source = "dossier.id")
    @Mapping(target = "dossierNumber", source = "dossier.number")
    @Mapping(target = "dossierObject", source = "dossier.object")
    @Mapping(target = "overdue",       ignore = true)
    @Mapping(target = "remainingDays", ignore = true)
    @Mapping(target = "memberCount",   ignore = true)
    @Mapping(target = "members",       ignore = true)
    InvestigationResponse toResponse(Investigation investigation);

    @Mapping(target = "overdue",       ignore = true)
    @Mapping(target = "remainingDays", ignore = true)
    @Mapping(target = "memberCount",   ignore = true)
    InvestigationSummaryResponse toSummaryResponse(Investigation investigation);

    @AfterMapping
    default void fillSummary(
            Investigation inv,
            @MappingTarget InvestigationSummaryResponse response) {

        response.setOverdue(inv.isOverdue());
        response.setRemainingDays(inv.getRemainingDays());

        response.setMemberCount(
                inv.getMembers() != null
                        ? (int) inv.getMembers().stream()
                        .filter(m -> Boolean.TRUE.equals(m.getActive()))
                        .count()
                        : 0
        );
    }
    @AfterMapping
    default void fillCalculated(
            Investigation inv,
            @MappingTarget InvestigationResponse response) {

        response.setOverdue(inv.isOverdue());
        response.setRemainingDays(inv.getRemainingDays());

        List<InvestigationMember> activeMembers = inv.getMembers() != null
                ? inv.getMembers().stream()
                .filter(m -> Boolean.TRUE.equals(m.getActive()))
                .toList()
                : Collections.emptyList();

        // ── LOG DIAGNOSTIC ──────────────────────────────
        System.out.println("[MAPPER] inv=" + inv.getId()
                + " | members_total=" + (inv.getMembers() != null ? inv.getMembers().size() : "NULL")
                + " | actifs=" + activeMembers.size());
        // ────────────────────────────────────────────────

        response.setMembers(
                activeMembers.stream()
                        .map(this::toMemberResponse)
                        .toList()
        );
        response.setMemberCount(activeMembers.size());
    }

    @Mapping(target = "dateAttribution", source = "createdAt", qualifiedByName = "toLocalDate")
    @Mapping(target = "active",          source = "active")
    InvestigationMemberResponse toMemberResponse(InvestigationMember member);

    @Named("toLocalDate")
    default LocalDate toLocalDate(Instant instant) {
        if (instant == null) return null;
        return instant.atZone(ZoneId.of("Africa/Ouagadougou")).toLocalDate();
    }
}