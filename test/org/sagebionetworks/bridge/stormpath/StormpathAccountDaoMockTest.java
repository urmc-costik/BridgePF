package org.sagebionetworks.bridge.stormpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.Email;
import org.sagebionetworks.bridge.models.accounts.EmailVerification;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.SignUp;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.services.SubpopulationService;

import com.google.common.collect.Lists;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.CustomData;
import com.stormpath.sdk.directory.Directory;
import com.stormpath.sdk.resource.ResourceException;
import com.stormpath.sdk.tenant.Tenant;

public class StormpathAccountDaoMockTest {

    private static final String PASSWORD = "P4ssword!";
    
    private Study study;
    
    @Before
    public void setUp() {
        study = new DynamoStudy();
        study.setIdentifier("test-study");
        study.setStormpathHref("http://some/dumb.href");
    }

    @Test
    public void verifyEmail() {
        StormpathAccountDao dao = new StormpathAccountDao();
        
        EmailVerification verification = new EmailVerification("tokenAAA");
        
        Client client = mock(Client.class);
        Tenant tenant = mock(Tenant.class);
        when(client.getCurrentTenant()).thenReturn(tenant);
        
        when(client.verifyAccountEmail("tokenAAA")).thenReturn(mock(com.stormpath.sdk.account.Account.class));
        
        dao.setStormpathClient(client);
        dao.setSubpopulationService(mockSubpopService());
        
        Account account = dao.verifyEmail(study, verification);
        assertNotNull(account);
        verify(client).verifyAccountEmail("tokenAAA");
    }

    @Test
    public void requestResetPassword() {
        String emailString = "bridge-tester+43@sagebridge.org";
        
        Application application = mock(Application.class);
        Directory directory = mock(Directory.class);
        Client client = mock(Client.class);
        when(client.getResource(study.getStormpathHref(), Directory.class)).thenReturn(directory);
        
        StormpathAccountDao dao = new StormpathAccountDao();
        dao.setStormpathApplication(application);
        dao.setStormpathClient(client);
        dao.setSubpopulationService(mockSubpopService());
        
        dao.requestResetPassword(study, new Email(study.getStudyIdentifier(), emailString));
        
        verify(client).getResource(study.getStormpathHref(), Directory.class);
        verify(application).sendPasswordResetEmail(emailString, directory);
    }
    
    @Test(expected = BridgeServiceException.class)
    public void requestPasswordRequestThrowsException() {
        String emailString = "bridge-tester+43@sagebridge.org";
        
        Application application = mock(Application.class);
        Directory directory = mock(Directory.class);
        Client client = mock(Client.class);
        when(client.getResource(study.getStormpathHref(), Directory.class)).thenReturn(directory);
        
        StormpathAccountDao dao = new StormpathAccountDao();
        dao.setStormpathApplication(application);
        dao.setStormpathClient(client);
        
        com.stormpath.sdk.error.Error error = mock(com.stormpath.sdk.error.Error.class);
        ResourceException e = new ResourceException(error);
        when(application.sendPasswordResetEmail(emailString, directory)).thenThrow(e);
        dao.setStormpathApplication(application);
        
        dao.requestResetPassword(study, new Email(study.getStudyIdentifier(), emailString));
    }

    @Test
    public void resetPassword() {
        StormpathAccountDao dao = new StormpathAccountDao();
        PasswordReset passwordReset = new PasswordReset("password", "sptoken");
        
        Application application = mock(Application.class);
        dao.setStormpathApplication(application);
        dao.setSubpopulationService(mockSubpopService());
        
        dao.resetPassword(passwordReset);
        verify(application).resetPassword(passwordReset.getSptoken(), passwordReset.getPassword());
        verifyNoMoreInteractions(application);
    }
    
    @Test
    public void stormpathAccountCorrectlyInitialized() {
        StormpathAccountDao dao = new StormpathAccountDao();
        
        Directory directory = mock(Directory.class);
        com.stormpath.sdk.account.Account stormpathAccount = mock(com.stormpath.sdk.account.Account.class);
        when(stormpathAccount.getCustomData()).thenReturn(mock(CustomData.class));
        Client client = mock(Client.class);
        
        when(client.instantiate(com.stormpath.sdk.account.Account.class)).thenReturn(stormpathAccount);
        when(client.getResource(study.getStormpathHref(), Directory.class)).thenReturn(directory);
        dao.setStormpathClient(client);
        dao.setSubpopulationService(mockSubpopService());
        
        String random = RandomStringUtils.randomAlphabetic(5);
        String email = "bridge-testing+"+random+"@sagebridge.org";
        SignUp signUp = new SignUp(random, email, PASSWORD, null, null);
        Account account = dao.signUp(study, signUp, false);
        assertNotNull(account);
        
        ArgumentCaptor<com.stormpath.sdk.account.Account> argument = ArgumentCaptor.forClass(com.stormpath.sdk.account.Account.class);
        verify(directory).createAccount(argument.capture(), anyBoolean());
        
        com.stormpath.sdk.account.Account acct = argument.getValue();
        verify(acct).setSurname("<EMPTY>");
        verify(acct).setGivenName("<EMPTY>");
        verify(acct).setUsername(random);
        verify(acct).setEmail(email);
        verify(acct).setPassword(PASSWORD);
    }

    @Test
    public void accountDisabled() {
        // mock stormpath client
        Directory mockDirectory = mock(Directory.class);
        Client mockClient = mock(Client.class);
        when(mockClient.getResource(study.getStormpathHref(), Directory.class)).thenReturn(mockDirectory);

        // mock stormpath application - Don't check the args to Application.authenticateAccount(). This is tested
        // elsewhere.
        com.stormpath.sdk.error.Error mockError = mock(com.stormpath.sdk.error.Error.class);
        when(mockError.getCode()).thenReturn(7101);
        ResourceException spException = new ResourceException(mockError);

        Application mockApplication = mock(Application.class);
        when(mockApplication.authenticateAccount(any())).thenThrow(spException);

        // setup dao
        StormpathAccountDao dao = new StormpathAccountDao();
        dao.setSubpopulationService(mockSubpopService());
        dao.setStormpathApplication(mockApplication);
        dao.setStormpathClient(mockClient);

        // execute and validate
        try {
            dao.authenticate(study, new SignIn("dummy-user", PASSWORD));
            fail("expected exception");
        } catch (BridgeServiceException ex) {
            assertEquals(HttpStatus.SC_LOCKED, ex.getStatusCode());
        }
    }

    private SubpopulationService mockSubpopService() {
        Subpopulation subpop = Subpopulation.create();
        subpop.setGuidString(study.getIdentifier());
        SubpopulationService subpopService = mock(SubpopulationService.class);
        when(subpopService.getSubpopulations(study.getStudyIdentifier())).thenReturn(Lists.newArrayList(subpop));
        return subpopService;
    }

}
