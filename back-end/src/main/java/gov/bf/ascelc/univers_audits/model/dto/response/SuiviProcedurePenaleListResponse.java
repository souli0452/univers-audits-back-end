package gov.bf.ascelc.univers_audits.model.dto.response;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuiviProcedurePenaleListResponse {
    private UUID investigationId;
    private List<SuiviProcedurePenaleResponse> suivis;
}
