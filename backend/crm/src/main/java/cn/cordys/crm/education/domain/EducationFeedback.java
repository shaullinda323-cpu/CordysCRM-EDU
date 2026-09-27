package cn.cordys.crm.education.domain;

import cn.cordys.common.domain.BaseModel;

import jakarta.persistence.Table;

import lombok.Data;

/** Each content edit creates a new revision; a staff report is not a delivery receipt. */
@Data
@Table(name = "edu_feedback")
public class EducationFeedback extends BaseModel {
    private String organizationId;
    private String sessionId;
    private String enrollmentId;
    private String requestKey;
    private String previousId;
    private Integer revision;
    private String content;
    private String status;
    private String confirmedUser;
    private Long confirmedTime;
    private String recipientContactId;
    private Long sentTime;
    private String sendEvidence;
    private String consentReference;
}
