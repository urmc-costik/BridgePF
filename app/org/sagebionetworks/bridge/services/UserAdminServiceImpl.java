package org.sagebionetworks.bridge.services;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Iterator;

import org.apache.commons.lang3.StringUtils;
import org.sagebionetworks.bridge.Roles;
import org.sagebionetworks.bridge.cache.CacheProvider;
import org.sagebionetworks.bridge.dao.AccountDao;
import org.sagebionetworks.bridge.dao.DistributedLockDao;
import org.sagebionetworks.bridge.dao.HealthIdDao;
import org.sagebionetworks.bridge.dao.ParticipantOption.SharingScope;
import org.sagebionetworks.bridge.exceptions.BridgeServiceException;
import org.sagebionetworks.bridge.json.DateUtils;
import org.sagebionetworks.bridge.models.ClientInfo;
import org.sagebionetworks.bridge.models.accounts.Account;
import org.sagebionetworks.bridge.models.accounts.SignIn;
import org.sagebionetworks.bridge.models.accounts.SignUp;
import org.sagebionetworks.bridge.models.accounts.User;
import org.sagebionetworks.bridge.models.accounts.UserSession;
import org.sagebionetworks.bridge.models.studies.Study;
import org.sagebionetworks.bridge.models.subpopulations.ConsentSignature;
import org.sagebionetworks.bridge.models.subpopulations.SubpopulationGuid;
import org.sagebionetworks.bridge.redis.RedisKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.common.base.Preconditions;

@Component("userAdminService")
public class UserAdminServiceImpl implements UserAdminService {

    private static final Logger logger = LoggerFactory.getLogger(UserAdminServiceImpl.class);

    private AuthenticationServiceImpl authenticationService;
    private AccountDao accountDao;
    private ConsentService consentService;
    private HealthDataService healthDataService;
    private HealthIdDao healthIdDao;
    private StudyService studyService;
    private SurveyResponseService surveyResponseService;
    private ScheduledActivityService scheduledActivityService;
    private ActivityEventService activityEventService;
    private DistributedLockDao lockDao;
    private CacheProvider cacheProvider;
    private ParticipantOptionsService optionsService;

    @Autowired
    public final void setAuthenticationService(AuthenticationServiceImpl authenticationService) {
        this.authenticationService = authenticationService;
    }
    @Autowired
    public final void setAccountDao(AccountDao accountDao) {
        this.accountDao = accountDao;
    }
    @Autowired
    public final void setConsentService(ConsentService consentService) {
        this.consentService = consentService;
    }
    @Autowired
    public final void setHealthDataService(HealthDataService healthDataService) {
        this.healthDataService = healthDataService;
    }
    @Autowired
    public final void setStudyService(StudyService studyService) {
        this.studyService = studyService;
    }
    @Autowired
    public final void setDistributedLockDao(DistributedLockDao lockDao) {
        this.lockDao = lockDao;
    }
    @Autowired
    public final void setHealthIdDao(HealthIdDao healthIdDao) {
        this.healthIdDao = healthIdDao;
    }
    @Autowired
    public final void setScheduledActivityService(ScheduledActivityService scheduledActivityService) {
        this.scheduledActivityService = scheduledActivityService;
    }
    @Autowired
    public final void setActivityEventService(ActivityEventService activityEventService) {
        this.activityEventService = activityEventService;
    }
    @Autowired
    public final void setSurveyResponseService(SurveyResponseService surveyResponseService) {
        this.surveyResponseService = surveyResponseService;
    }
    @Autowired
    public final void setCacheProvider(CacheProvider cache) {
        this.cacheProvider = cache;
    }
    @Autowired
    public final void setParticipantOptionsService(ParticipantOptionsService optionsService) {
        this.optionsService = optionsService;
    }
    
    /**
     * Note that currently, the ability to consent someone to a subpopulation other than the default 
     * subpopulation is not supported in the API.
     */
    @Override
    public UserSession createUser(SignUp signUp, Study study, SubpopulationGuid subpopGuid,
            boolean signUserIn, boolean consentUser) {
        checkNotNull(study, "Study cannot be null");
        checkNotNull(signUp, "Sign up cannot be null");
        checkNotNull(signUp.getEmail(), "Sign up email cannot be null");

        authenticationService.signUp(study, signUp, false);

        SignIn signIn = new SignIn(signUp.getUsername(), signUp.getPassword());
        UserSession newUserSession = authenticationService.signIn(study, ClientInfo.UNKNOWN_CLIENT, signIn);

        if (consentUser) {
            String name = String.format("[Signature for %s]", signUp.getEmail());
            ConsentSignature consent = new ConsentSignature.Builder().withName(name)
                    .withBirthdate("1989-08-19").withSignedOn(DateUtils.getCurrentMillisFromEpoch()).build();

            SubpopulationGuid consentTo = (subpopGuid != null) ? subpopGuid
                    : SubpopulationGuid.create(study.getIdentifier());

            consentService.consentToResearch(study, consentTo,
                    newUserSession.getUser(), consent, SharingScope.NO_SHARING, false);
        }

        if (!signUserIn) {
            authenticationService.signOut(newUserSession);
            newUserSession = null;
        }
        return newUserSession;
    }

    @Override
    public void deleteUser(Study study, String email) {
        checkNotNull(study);
        Preconditions.checkArgument(StringUtils.isNotBlank(email));
        Account account = accountDao.getAccount(study, email);
        if (account != null) {
            deleteUser(account);
        }
    }

    void deleteUser(Account account) {
        checkNotNull(account);
        int retryCount = 0;
        boolean shouldRetry = true;
        while (shouldRetry) {
            boolean deleted = deleteUserAttempt(account);
            if (deleted) {
                return;
            }
            shouldRetry = retryCount < 5;
            retryCount++;
            try {
                Thread.sleep(100 * 2 ^ retryCount);
            } catch(InterruptedException ie) {
                throw new BridgeServiceException(ie);
            }
        }
    }

    @Override
    public void deleteAllUsers(Roles role) {
        Iterator<Account> iterator = accountDao.getAllAccounts();
        while(iterator.hasNext()) {
            Account account = iterator.next();
            if (account.getRoles().contains(role)) {
                deleteUser(account);
            }
        }
    }

    private boolean deleteUserAttempt(Account account) {
        String key = RedisKey.USER_LOCK.getRedisKey(account.getEmail());
        String lock = null;
        boolean success = false;
        try {
            lock = lockDao.acquireLock(User.class, key);
            if (account != null) {
                Study study = studyService.getStudy(account.getStudyIdentifier());
                deleteUserInStudy(study, account);
                accountDao.deleteAccount(study, account.getEmail());
                // Check if the delete succeeded
                success = accountDao.getAccount(study, account.getEmail()) == null ? true : false;
                cacheProvider.removeSessionByUserId(account.getId());
            }
        } catch(Throwable t) {
            success = false;
            logger.error(t.getMessage(), t);
        } finally {
            // This used to silently fail, not there is a precondition check that
            // throws an exception. If there has been an exception, lock == null
            if (lock != null) {
                lockDao.releaseLock(User.class, key, lock);
            }
        }
        return success;
    }

    private boolean deleteUserInStudy(Study study, Account account) throws BridgeServiceException {
        checkNotNull(study);
        checkNotNull(account);

        try {
            // health id/code are not assigned until consent is given. They may not exist.
            if (account.getHealthId() != null) {
                // This is the fastest way to do this that I know of
                String healthCode = healthIdDao.getCode(account.getHealthId());
                // We expect to have health code, but when tests fail, we can get users who have signed in 
                // and do not have a health code.
                if (!StringUtils.isBlank(healthCode)) {
                    consentService.deleteAllConsentsForUser(study, healthCode);
                    healthDataService.deleteRecordsForHealthCode(healthCode);
                    scheduledActivityService.deleteActivitiesForUser(healthCode);
                    activityEventService.deleteActivityEvents(healthCode);
                    surveyResponseService.deleteSurveyResponses(healthCode);
                    optionsService.deleteAllParticipantOptions(healthCode);
                }
            }
            return true;
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
            return false;
        }
    }
}
