package gov.bf.ascelc.univers_audits.service.impl;

import gov.bf.ascelc.univers_audits.model.dto.request.TypeInfractionRequest;
import gov.bf.ascelc.univers_audits.model.entity.TypeInfraction;
import gov.bf.ascelc.univers_audits.repository.TypeInfractionRepository;
import gov.bf.ascelc.univers_audits.shared.exceptions.ConflictException;
import gov.bf.ascelc.univers_audits.shared.exceptions.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TypeInfractionServiceImplTest {

    @Mock
    private TypeInfractionRepository repository;

    @InjectMocks
    private TypeInfractionServiceImpl service;

    @Test
    void create_savesNewInfractionType() {
        when(repository.existsByCode("TRAFIC_INFLUENCE")).thenReturn(false);
        when(repository.save(any(TypeInfraction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        TypeInfractionRequest request = TypeInfractionRequest.builder()
                .code("TRAFIC_INFLUENCE")
                .libelle("Trafic d'influence")
                .impliqueDdip(false)
                .actif(true)
                .ordre(3)
                .build();

        TypeInfraction result = service.create(request);

        assertThat(result.getCode()).isEqualTo("TRAFIC_INFLUENCE");
        assertThat(result.getLibelle()).isEqualTo("Trafic d'influence");
        verify(repository).save(any(TypeInfraction.class));
    }

    @Test
    void create_throwsConflictWhenCodeAlreadyExists() {
        when(repository.existsByCode("ENRICHISSEMENT_ILLICITE")).thenReturn(true);

        TypeInfractionRequest request = TypeInfractionRequest.builder()
                .code("ENRICHISSEMENT_ILLICITE")
                .libelle("Enrichissement illicite")
                .impliqueDdip(true)
                .actif(true)
                .ordre(1)
                .build();

        assertThatThrownBy(() -> service.create(request))
                .isInstanceOf(ConflictException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void update_throwsWhenCodeUnknown() {
        when(repository.findByCode("INCONNU")).thenReturn(Optional.empty());

        TypeInfractionRequest request = TypeInfractionRequest.builder()
                .code("INCONNU")
                .libelle("Libellé")
                .impliqueDdip(false)
                .actif(true)
                .ordre(1)
                .build();

        assertThatThrownBy(() -> service.update("INCONNU", request))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
