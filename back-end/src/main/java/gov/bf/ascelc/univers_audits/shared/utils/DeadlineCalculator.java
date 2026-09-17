package gov.bf.ascelc.univers_audits.shared.utils;

import gov.bf.ascelc.univers_audits.repository.JourFerieRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@Component
@RequiredArgsConstructor
public class DeadlineCalculator {

    private final JourFerieRepository jourFerieRepository;

    /**
     * Avance depuis {@code from} jusqu'à avoir franchi {@code joursOuvrables} jours
     * ouvrables (ni samedi, ni dimanche, ni jour férié actif).
     */
    public Instant addBusinessDays(Instant from, int joursOuvrables) {
        ZonedDateTime fromZoned = from.atZone(ZoneOffset.UTC);
        LocalDate current = fromZoned.toLocalDate();
        int remaining = joursOuvrables;

        while (remaining > 0) {
            current = current.plusDays(1);
            if (isBusinessDay(current)) {
                remaining--;
            }
        }

        return current.atTime(fromZoned.toLocalTime()).atZone(ZoneOffset.UTC).toInstant();
    }

    /**
     * Calcul calendaire simple, sans exclusion de jours — centralise le calcul déjà
     * utilisé partout avant ce chantier, pour que les appelants n'aient qu'une seule
     * dépendance (ce composant) au lieu de dupliquer {@code plusSeconds(...)}.
     */
    public Instant addCalendarDays(Instant from, int jours) {
        return from.plusSeconds((long) jours * 24 * 3600);
    }

    private boolean isBusinessDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        if (day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY) {
            return false;
        }
        return !jourFerieRepository.existsByDateAndActifTrue(date);
    }
}
