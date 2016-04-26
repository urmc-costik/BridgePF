package org.sagebionetworks.bridge.stormpath;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import com.stormpath.sdk.group.Group;
import com.stormpath.sdk.group.GroupList;
import com.stormpath.sdk.impl.account.DefaultAccount;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.http.HttpStatus;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.sagebionetworks.bridge.BridgeConstants;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.dynamodb.DynamoStudy;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.Email;
import org.sagebionetworks.bridge.models.accounts.EmailVerification;
import org.sagebionetworks.bridge.models.accounts.HealthId;
import org.sagebionetworks.bridge.models.accounts.PasswordReset;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.StudyParticipant;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.Subpopulation;
import org.sagebionetworks.bridge.services.HealthCodeService;
import org.sagebionetworks.bridge.services.SubpopulationService;

import com.google.common.collect.Lists;
import com.stormpath.sdk.application.Application;
import com.stormpath.sdk.authc.AuthenticationResult;
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
        
        HealthCodeService healthCodeService = mock(HealthCodeService.class);
        dao.setHealthCodeService(healthCodeService);
        
        Directory directory = mock(Directory.class);
        com.stormpath.sdk.account.Account stormpathAccount = mock(com.stormpath.sdk.account.Account.class);
        when(stormpathAccount.getCustomData()).thenReturn(mock(CustomData.class));
        Client client = mock(Client.class);
        
        when(client.instantiate(com.stormpath.sdk.account.Account.class)).thenReturn(stormpathAccount);
        when(client.getResource(study.getStormpathHref(), Directory.class)).thenReturn(directory);
        dao.setStormpathClient(client);
        dao.setSubpopulationService(mockSubpopService());
        
        HealthId healthId = mock(HealthId.class);
        doReturn(healthId).when(healthCodeService).createMapping(study);
        
        String random = RandomStringUtils.randomAlphabetic(5);
        String email = "bridge-testing+"+random+"@sagebridge.org";
        StudyParticipant participant = new StudyParticipant.Builder().withEmail(email).withPassword(PASSWORD).build();
        Account account = dao.signUp(study, participant, false);
        assertNotNull(account);
        
        ArgumentCaptor<com.stormpath.sdk.account.Account> argument = ArgumentCaptor.forClass(com.stormpath.sdk.account.Account.class);
        verify(directory).createAccount(argument.capture(), anyBoolean());
        
        com.stormpath.sdk.account.Account acct = argument.getValue();
        verify(acct).setSurname("<EMPTY>");
        verify(acct).setGivenName("<EMPTY>");
        verify(acct).setUsername(email);
        verify(acct).setEmail(email);
        verify(acct).setPassword(PASSWORD);
    }
    
    @Test
    public void authenticate() {
        // mock stormpath director
        Directory mockDirectory = mock(Directory.class);
        
        // mock stormpath client
        Client mockClient = mock(Client.class);
        when(mockClient.getResource(study.getStormpathHref(), Directory.class)).thenReturn(mockDirectory);

        // mock subpopulation service
        SubpopulationService mockSubpopService = mock(SubpopulationService.class);
        
        // mock stormpath account
        com.stormpath.sdk.account.Account mockAcct = mock(com.stormpath.sdk.account.Account.class);
        when(mockAcct.getGivenName()).thenReturn("Test");
        when(mockAcct.getSurname()).thenReturn("User");
        when(mockAcct.getEmail()).thenReturn("email@email.com");
        
        // mock authentication result
        AuthenticationResult mockResult = mock(AuthenticationResult.class);
        when(mockResult.getAccount()).thenReturn(mockAcct);
        
        // mock stormpath application
        Application mockApplication = mock(Application.class);
        when(mockApplication.authenticateAccount(any())).thenReturn(mockResult);
        
        // wire up DAO
        StormpathAccountDao dao = new StormpathAccountDao();
        dao.setStormpathClient(mockClient);
        dao.setStormpathApplication(mockApplication);
        dao.setSubpopulationService(mockSubpopService);
        
        // authenticate
        Account account = dao.authenticate(study, new SignIn("dummy-user", PASSWORD));
        
        // Just verify a few fields, the full object initialization is tested elsewhere.
        assertEquals("Test", account.getFirstName());
        assertEquals("User", account.getLastName());
        assertEquals("email@email.com", account.getEmail());
        
        // verify eager fetch occurring. Can't verify AuthenticationRequest configuration because 
        // you can set it but settings themselves are hidden in implementation. 
        verify(mockResult).getAccount();
        verify(mockAcct).getCustomData();
        verify(mockAcct).getGroups();
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

    private com.stormpath.sdk.account.Account setupGroupChangeTest(Set<String> oldGroupSet, Set<Roles> newGroupSet) {
        // mock Stormpath account
        List<Group> mockGroupJavaList = new ArrayList<>();
        for (String oneGroupName : oldGroupSet) {
            Group mockGroup = mock(Group.class);
            when(mockGroup.getName()).thenReturn(oneGroupName);
            mockGroupJavaList.add(mockGroup);
        }

        GroupList mockGroupSpList = mock(GroupList.class);
        when(mockGroupSpList.iterator()).thenReturn(mockGroupJavaList.iterator());

        // Due to some funkiness in Stormpath's type tree, DefaultAccount is the only public class we can mock.
        com.stormpath.sdk.account.Account mockSpAccount = mock(DefaultAccount.class);
        when(mockSpAccount.getCustomData()).thenReturn(mock(CustomData.class));
        when(mockSpAccount.getGroups()).thenReturn(mockGroupSpList);

        // mock Bridge account
        StormpathAccount mockAccount = mock(StormpathAccount.class);
        when(mockAccount.getAccount()).thenReturn(mockSpAccount);
        when(mockAccount.getRoles()).thenReturn(newGroupSet);

        // execute
        StormpathAccountDao dao = new StormpathAccountDao();
        dao.updateAccount(study, mockAccount);

        // return this, so the caller can verify back-end mock calls
        return mockSpAccount;
    }

    @Test
    public void updatingAccountWithNoGroupChanges() {
        Set<String> oldGroupSet = ImmutableSet.of("test_users", "worker");
        Set<Roles> newGroupSet = EnumSet.of(Roles.TEST_USERS, Roles.WORKER);
        com.stormpath.sdk.account.Account mockSpAccount = setupGroupChangeTest(oldGroupSet, newGroupSet);
        verify(mockSpAccount, never()).addGroup(anyString());
        verify(mockSpAccount, never()).removeGroup(anyString());
    }

    @Test
    public void updatingAccountWithAddedGroups() {
        Set<String> oldGroupSet = ImmutableSet.of("test_users", "worker");
        Set<Roles> newGroupSet = EnumSet.of(Roles.RESEARCHER, Roles.TEST_USERS, Roles.WORKER);
        com.stormpath.sdk.account.Account mockSpAccount = setupGroupChangeTest(oldGroupSet, newGroupSet);
        verify(mockSpAccount, times(1)).addGroup("researcher");
        verify(mockSpAccount, times(1)).addGroup(anyString());
        verify(mockSpAccount, never()).removeGroup(anyString());
    }

    @Test
    public void updatingAccountWithRemovedGroups() {
        Set<String> oldGroupSet = ImmutableSet.of("test_users", "worker");
        Set<Roles> newGroupSet = EnumSet.of(Roles.TEST_USERS);
        com.stormpath.sdk.account.Account mockSpAccount = setupGroupChangeTest(oldGroupSet, newGroupSet);
        verify(mockSpAccount, never()).addGroup(anyString());
        verify(mockSpAccount, times(1)).removeGroup("worker");
        verify(mockSpAccount, times(1)).removeGroup(anyString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void getStudyPagedAccountsRejectsPageSizeTooSmall() {
        StormpathAccountDao dao = new StormpathAccountDao();
        dao.getPagedAccountSummaries(study, 0, BridgeConstants.API_MINIMUM_PAGE_SIZE-1, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void getStudyPagedAccountsRejectsPageSizeTooLarge() {
        StormpathAccountDao dao = new StormpathAccountDao();
        dao.getPagedAccountSummaries(study, 0, BridgeConstants.API_MAXIMUM_PAGE_SIZE+1, null);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void getStudyPagedAccountsRejectsNonsenseOffsetBy() {
        StormpathAccountDao dao = new StormpathAccountDao();
        dao.getPagedAccountSummaries(study, -10, BridgeConstants.API_DEFAULT_PAGE_SIZE, null);
    }
    
    private SubpopulationService mockSubpopService() {
        Subpopulation subpop = Subpopulation.create();
        subpop.setGuidString(study.getIdentifier());
        SubpopulationService subpopService = mock(SubpopulationService.class);
        when(subpopService.getSubpopulations(study.getStudyIdentifier())).thenReturn(Lists.newArrayList(subpop));
        return subpopService;
    }

}
