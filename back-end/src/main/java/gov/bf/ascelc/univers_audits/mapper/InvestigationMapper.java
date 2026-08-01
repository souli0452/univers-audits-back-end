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

    // NOTE — bug MapStruct/Lombok confirme (voir DeclarantMapper/DossierMapper/
    // DossierDetailsMapper) : @AfterMapping n'est jamais invoque quand la cible
    // est un @Builder Lombok, ce qui est le cas des deux DTO ci-dessous. Sans ce
    // correctif, overdue/remainingDays/memberCount/members restaient toujours
    // vides — appele directement comme reponse finale dans une dizaine
    // d'endroits de InvestigationServiceImpl.

    default InvestigationResponse toResponse(Investigation investigation) {
        InvestigationResponse response = mapToResponse(investigation);
        if (response != null) {
            fillCalculated(investigation, response);
        }
        return response;
    }

    @Mapping(target = "dossierId",     source = "dossier.id")
    @Mapping(target = "dossierNumber", source = "dossier.number")
    @Mapping(target = "dossierObject", source = "dossier.object")
    @Mapping(target = "overdue",       ignore = true)
    @Mapping(target = "remainingDays", ignore = true)
    @Mapping(target = "memberCount",   ignore = true)
    @Mapping(target = "members",       ignore = true)
    InvestigationResponse mapToResponse(Investigation investigation);

    default InvestigationSummaryResponse toSummaryResponse(Investigation investigation) {
        InvestigationSummaryResponse response = mapToSummaryResponse(investigation);
        if (response != null) {
            fillSummary(investigation, response);
        }
        return response;
    }

    @Mapping(target = "overdue",       ignore = true)
    @Mapping(target = "remainingDays", ignore = true)
    @Mapping(target = "memberCount",   ignore = true)
    InvestigationSummaryResponse mapToSummaryResponse(Investigation investigation);

    default void fillSummary(
            Investigation inv,
            InvestigationSummaryResponse response) {

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
    default void fillCalculated(
            Investigation inv,
            InvestigationResponse response) {

        response.setOverdue(inv.isOverdue());
        response.setRemainingDays(inv.getRemainingDays());

        List<InvestigationMember> activeMembers = inv.getMembers() != null
                ? inv.getMembers().stream()
                .filter(m -> Boolean.TRUE.equals(m.getActive()))
                .toList()
                : Collections.emptyList();


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