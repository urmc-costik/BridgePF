package org.sagebionetworks.bridge.dao;

import java.util.List;

import org.sagebionetworks.bridge.models.surveys.SurveyAnswer;
import org.sagebionetworks.bridge.models.surveys.SurveyResponse;

public interface SurveyResponseDao {

    public SurveyResponse createSurveyResponse(String surveyGuid, long surveyVersionedOn, String healthDataCode,
            List<SurveyAnswer> answers);
    
    public SurveyResponse getSurveyResponse(String guid);
    
    public SurveyResponse appendSurveyAnswers(SurveyResponse response, List<SurveyAnswer> answers);
    
    public void deleteSurveyResponse(SurveyResponse response);
    
}
