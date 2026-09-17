package gov.bf.ascelc.univers_audits.shared.utils;

import gov.bf.ascelc.univers_audits.repository.JourFerieRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeadlineCalculatorTest {

    @Mock
    private JourFerieRepository jourFerieRepository;

    @InjectMocks
    private DeadlineCalculator calculator;

    @Test
    void addBusinessDays_skipsWeekend() {
        // Vendredi 2027-01-01 (verifie : 2027-01-01 est un vendredi)
        Instant vendredi = LocalDate.of(2027, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant();

        Instant result = calculator.addBusinessDays(vendredi, 1);

        // +1 jour ouvrable depuis vendredi doit sauter samedi/dimanche -> lundi 2027-01-04
        assertThat(result).isEqualTo(
                LocalDate.of(2027, 1, 4).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    @Test
    void addBusinessDays_skipsActiveJourFerie() {
        // Lundi 2027-01-04, jour ferie actif le mardi 2027-01-05
        Instant lundi = LocalDate.of(2027, 1, 4).atStartOfDay(ZoneOffset.UTC).toInstant();
        when(jourFerieRepository.existsByDateAndActifTrue(LocalDate.of(2027, 1, 5)))
                .thenReturn(true);
        when(jourFerieRepository.existsByDateAndActifTrue(LocalDate.of(2027, 1, 6)))
                .thenReturn(false);

        Instant result = calculator.addBusinessDays(lundi, 1);

        // +1 jour ouvrable depuis lundi doit sauter le mardi ferie -> mercredi 2027-01-06
        assertThat(result).isEqualTo(
                LocalDate.of(2027, 1, 6).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    @Test
    void addBusinessDays_ignoresInactiveJourFerie() {
        // Lundi 2027-01-04, jour ferie INACTIF le mardi -> compte comme jour ouvrable normal
        Instant lundi = LocalDate.of(2027, 1, 4).atStartOfDay(ZoneOffset.UTC).toInstant();
        when(jourFerieRepository.existsByDateAndActifTrue(LocalDate.of(2027, 1, 5)))
                .thenReturn(false);

        Instant result = calculator.addBusinessDays(lundi, 1);

        assertThat(result).isEqualTo(
                LocalDate.of(2027, 1, 5).atStartOfDay(ZoneOffset.UTC).toInstant());
    }

    @Test
    void addBusinessDays_zeroDays_returnsFromUnchanged() {
        Instant lundi = LocalDate.of(2027, 1, 4).atStartOfDay(ZoneOffset.UTC).toInstant();

        Instant result = calculator.addBusinessDays(lundi, 0);

        assertThat(result).isEqualTo(lundi);
    }

    @Test
    void addCalendarDays_simpleDelegation() {
        Instant from = Instant.parse("2027-01-01T10:00:00Z");

        Instant result = calculator.addCalendarDays(from, 5);

        assertThat(result).isEqualTo(from.plusSeconds(5L * 24 * 3600));
    }
}
