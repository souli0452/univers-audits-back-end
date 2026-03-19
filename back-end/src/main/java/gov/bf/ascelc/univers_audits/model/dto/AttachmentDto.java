package gov.bf.ascelc.univers_audits.model.dto;
import gov.bf.ascelc.univers_audits.abstracts.AuditEntityDto;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
@AllArgsConstructor
public class AttachmentDto extends AuditEntityDto {

    private String fileName;
    private String type;
    private String uploadDate;
    private String description;
    private String filePath;
}
