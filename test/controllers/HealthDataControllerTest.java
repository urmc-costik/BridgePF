package controllers;

import static org.fest.assertions.Assertions.assertThat;
import static play.mvc.Http.Status.OK;
import static play.test.Helpers.running;
import static play.test.Helpers.testServer;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.apache.commons.httpclient.HttpStatus;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.sagebionetworks.bridge.TestUtils;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecord;
import org.sagebionetworks.bridge.models.healthdata.HealthDataRecordImpl;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;

import play.libs.WS.Response;
import static org.sagebionetworks.bridge.TestConstants.*;

public class HealthDataControllerTest {

    private ObjectMapper mapper = new ObjectMapper();

    public HealthDataControllerTest() {
        mapper.setSerializationInclusion(Include.NON_NULL);
    }
    
    private List<HealthDataRecord> getTestRecords() throws Exception {
        return getTestRecords(1399666566890L, 1399666566890L);
    }
    
    // The test record used here is a medication record, which can have 
    // duration (no end date), which makes it important for the date-based
    // query methods of the service.
    private List<HealthDataRecord> getTestRecords(long startDate, long endDate) throws Exception {
        HealthDataRecord record = new HealthDataRecordImpl();
        record.setStartDate(startDate);
        if (endDate > 0) {
            record.setEndDate(endDate);    
        }
        ObjectNode data = JsonNodeFactory.instance.objectNode();
        data.put("medication", "Lionipril");
        data.put("dosage", "10mg");
        data.put("frequency", "1x/day");
        record.setData(data);
        
        return Lists.newArrayList(record);
    }
    
    private String retrieveNewId(Response response) {
        JsonNode body = response.asJson();
        JsonNode payload = body.get("payload");
        JsonNode ids = payload.get("ids");
        return ids.get(0).asText();
    }
    
    private List<String> getIds(Response response) {
        JsonNode body = response.asJson();
        ArrayNode array = (ArrayNode)body.get("payload");
        List<String> ids = Lists.newArrayList();
        for (int i=0; i < array.size(); i++) {
            JsonNode child = array.get(i);
            ids.add(child.get("recordId").asText());
        }
        Collections.sort(ids);
        return ids;
    }
    
    @Before
    public void deleteRecordsBefore() throws Exception {
        TestUtils.deleteAllHealthData();
    }
    
    @After
    public void deleteRecordsAfter() throws Exception {
        TestUtils.deleteAllHealthData();
    }
    
    @Test
    public void appendHealthData() throws Exception {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                String sessionToken = TestUtils.signIn();
                List<HealthDataRecord> records = getTestRecords();
                
                Response response = TestUtils.getURL(sessionToken, TRACKER_URL)
                        .post(mapper.writeValueAsString(records)).get(TIMEOUT);
                assertThat(response.getStatus()).isEqualTo(OK);
                
                String id = retrieveNewId(response);
                assertThat(id).isNotEmpty();
                
                TestUtils.signOut();
            }
        });
    }

    @Test
    public void getAllHealthData() throws Exception {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                String sessionToken = TestUtils.signIn();
                
                TestUtils.getURL(sessionToken, TRACKER_URL).post(mapper.writeValueAsString(getTestRecords()))
                        .get(TIMEOUT);
                TestUtils.getURL(sessionToken, TRACKER_URL).post(mapper.writeValueAsString(getTestRecords()))
                        .get(TIMEOUT);
                TestUtils.getURL(sessionToken, TRACKER_URL).post(mapper.writeValueAsString(getTestRecords()))
                        .get(TIMEOUT);
                
                Response response = TestUtils.getURL(sessionToken, TRACKER_URL).get().get(TIMEOUT);
                
                JsonNode body = response.asJson();
                ArrayNode array = (ArrayNode)body.get("payload");
                assertThat(array.size()).isEqualTo(3);
                
                TestUtils.signOut();
            }
        });
    }
    
    @Test
    public void getHealthDataByDateRange() throws Exception {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                String sessionToken = TestUtils.signIn();
                
                // Time ranges used in this test, and where they overlap with the 3 test windows or not.
                //       1        1...<2
                //       2        1............3
                //       3                                                 4............6
                //       4                     3...........................4
                //       5                                                       >5.....6
                //       6                     3............................................
                //
                //                    2__________________________________________5
                //                1____________3
                //                                                         4_____5
                
                long threeDays = (1000L * 60L * 60L * 24L *3L);
                
                long thousandDaysAgo = new Date().getTime() - (1000*60*60*24*1000);
                long time1 = thousandDaysAgo + threeDays;
                long time2 = thousandDaysAgo + threeDays*2;
                long time3 = thousandDaysAgo + threeDays*3;
                long time4 = thousandDaysAgo + threeDays*4;
                long time5 = thousandDaysAgo + threeDays*5;
                long time6 = thousandDaysAgo + threeDays*6;

                List<HealthDataRecord> records = getTestRecords(time1, time2-1);
                Response response = TestUtils.getURL(sessionToken, TRACKER_URL).post(mapper.writeValueAsString(records)).get(TIMEOUT);
                String id1 = retrieveNewId(response);

                records = getTestRecords(time1, time3);
                response = TestUtils.getURL(sessionToken, TRACKER_URL).post(mapper.writeValueAsString(records)).get(TIMEOUT);
                String id2 = retrieveNewId(response);
                
                records = getTestRecords(time4, time6);
                response = TestUtils.getURL(sessionToken, TRACKER_URL).post(mapper.writeValueAsString(records)).get(TIMEOUT);
                String id3 = retrieveNewId(response);
                
                records = getTestRecords(time3, time4);
                response = TestUtils.getURL(sessionToken, TRACKER_URL).post(mapper.writeValueAsString(records)).get(TIMEOUT);
                String id4 = retrieveNewId(response);
                
                records = getTestRecords(time5+1, time6);
                response = TestUtils.getURL(sessionToken, TRACKER_URL).post(mapper.writeValueAsString(records)).get(TIMEOUT);
                retrieveNewId(response); // never appears in queries
                
                records = getTestRecords(time3, 0);
                response = TestUtils.getURL(sessionToken, TRACKER_URL).post(mapper.writeValueAsString(records)).get(TIMEOUT);
                String id6 = retrieveNewId(response);
                
                String queryPath = String.format("/%s/%s", Long.toString(time2), Long.toString(time5));
                response = TestUtils.getURL(sessionToken, TRACKER_URL + queryPath).get().get(TIMEOUT);
                List<String> ids = getIds(response);
                assertThat(ids).contains(id2,id3,id4,id6);
                
                queryPath = String.format("/%s/%s", Long.toString(time1), Long.toString(time3));
                response = TestUtils.getURL(sessionToken, TRACKER_URL + queryPath).get().get(TIMEOUT);
                ids = getIds(response);
                assertThat(ids).contains(id1,id2,id4,id6);
                
                queryPath = String.format("/%s/%s", Long.toString(time4), Long.toString(time5));
                response = TestUtils.getURL(sessionToken, TRACKER_URL + queryPath).get().get(TIMEOUT);
                ids = getIds(response);
                assertThat(ids).contains(id3,id4,id6);
                
                TestUtils.signOut();
            }
        });
    }
    
    @Test
    public void updateHealthDataRecord() throws Exception {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                String sessionToken = TestUtils.signIn();

                List<HealthDataRecord> records = getTestRecords();
                
                Response response = TestUtils.getURL(sessionToken, TRACKER_URL).post(mapper.writeValueAsString(records)).get(TIMEOUT);
                assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);

                // Get the id and set it on the object
                String id = retrieveNewId(response);
                
                // Add the ID, change a value
                records.get(0).setRecordId(id);
                ObjectNode onode = (ObjectNode)records.get(0).getData();
                onode.put("systolic", 200L);

                // Save it (update)
                response = TestUtils.getURL(sessionToken, RECORD_URL + id).post(mapper.writeValueAsString(records.get(0))).get(TIMEOUT);
                assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);

                // Get it and verify that it was persisted.
                response = TestUtils.getURL(sessionToken, RECORD_URL + id).get().get(TIMEOUT);
                JsonNode body = response.asJson();
                JsonNode payload = body.get("payload");
                long valueSaved = payload.get("data").get("systolic").asLong();
                assertThat(valueSaved).isEqualTo(200L);
                
                TestUtils.signOut();
            }
        });
    }
    
    @Test
    public void deleteHealthDataRecord() throws Exception {
        running(testServer(3333), new TestUtils.FailableRunnable() {
            public void testCode() throws Exception {
                String sessionToken = TestUtils.signIn();
                
                List<HealthDataRecord> records = getTestRecords();
                
                // Create a record, retrieve its ID
                Response response = TestUtils.getURL(sessionToken, TRACKER_URL).post(mapper.writeValueAsString(records)).get(TIMEOUT);
                String id = retrieveNewId(response);
                
                response = TestUtils.getURL(sessionToken, RECORD_URL + id).delete().get(TIMEOUT);
                assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_OK);
                
                // Now this should generate a not found
                response = TestUtils.getURL(sessionToken, RECORD_URL + id).get().get(TIMEOUT);
                assertThat(response.getStatus()).isEqualTo(HttpStatus.SC_NOT_FOUND);
                
                TestUtils.signOut();
            }
        });
    }
}
