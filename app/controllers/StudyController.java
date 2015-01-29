package controllers;

import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.StudyInfo;
import org.sagebionetworks.bridge.models.VersionHolder;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.services.StudyService;
import org.sagebionetworks.bridge.services.UserProfileService;

import com.google.common.base.Function;
import com.google.common.collect.Lists;

import play.mvc.Result;

public class StudyController extends BaseController {

    private StudyService studyService;
    
    private UserProfileService userProfileService;

    public void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    
    public void setUserProfileService(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    public Result getStudyForResearcher() throws Exception {
        // We want a signed in exception before a study not found exception
        // getAuthenticatedSession();
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearchOrAdminSession(study);
        return okResult(new StudyInfo(study));
    }
    
    public Result sendStudyParticipantsRoster() throws Exception {
        Study study = studyService.getStudyByHostname(getHostname());
        // Researchers only, administrators cannot get this list so easily
        getAuthenticatedResearchSession(study);
        
        // One thing we can do here is verify that an email address has been set for the email
        // that is about to be send.
        if (StringUtils.isBlank(study.getConsentNotificationEmail())) {
            throw new BridgeServiceException("No consent notification contact email exists for this study. You must update the study with this contact before retrieving a roster of study participants.");
        }
        userProfileService.sendStudyParticipantRoster(study);
        return okResult("A roster of study participants will be emailed to the study's consent notification contact.");
    }

    public Result updateStudyForResearcher() throws Exception {
        // We want a signed in exception before a study not found exception
        // getAuthenticatedSession();
        Study study = studyService.getStudyByHostname(getHostname());
        getAuthenticatedResearchOrAdminSession(study);

        Study studyUpdate = DynamoStudy.fromJson(requestToJSON(request()));
        studyUpdate.setIdentifier(study.getIdentifier());
        studyUpdate = studyService.updateStudy(studyUpdate);
        return okResult(new VersionHolder(studyUpdate.getVersion()));
    }

    public Result updateStudy(String identifier) throws Exception {
        getAuthenticatedAdminSession();

        Study studyUpdate = DynamoStudy.fromJson(requestToJSON(request()));
        studyUpdate = studyService.updateStudy(studyUpdate);
        return okResult(new VersionHolder(studyUpdate.getVersion()));
    }

    public Result getStudy(String identifier) throws Exception {
        getAuthenticatedAdminSession();

        Study study = studyService.getStudyByIdentifier(identifier);
        return okResult(new StudyInfo(study));
    }

    public Result getAllStudies() throws Exception {
        getAuthenticatedAdminSession();

        List<StudyInfo> studies = Lists.transform(studyService.getStudies(), new Function<Study,StudyInfo>() {
            @Override
            public StudyInfo apply(Study study) {
                return new StudyInfo(study);
            }
        });
        return okResult(studies);
    }

    public Result createStudy() throws Exception {
        getAuthenticatedAdminSession();

        Study study = DynamoStudy.fromJson(requestToJSON(request()));
        study = studyService.createStudy(study);
        return okResult(new VersionHolder(study.getVersion()));
    }

    public Result deleteStudy(String identifier) throws Exception {
        getAuthenticatedAdminSession();

        studyService.deleteStudy(identifier);
        return okResult("Study deleted.");
    }

}
