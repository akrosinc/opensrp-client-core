package org.smartregister.login;

import android.app.Activity;
import android.content.DialogInterface;
import android.support.v7.app.AppCompatActivity;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.reflect.Whitebox;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.smartregister.AllConstants;
import org.smartregister.BaseRobolectricUnitTest;
import org.smartregister.Context;
import org.smartregister.CoreLibrary;
import org.smartregister.R;
import org.smartregister.SyncConfiguration;
import org.smartregister.domain.LoginResponse;
import org.smartregister.domain.TimeStatus;
import org.smartregister.domain.jsonmapping.LoginResponseData;
import org.smartregister.domain.jsonmapping.Time;
import org.smartregister.domain.jsonmapping.User;
import org.smartregister.listener.OnCompleteClearDataCallback;
import org.smartregister.multitenant.ResetAppHelper;
import org.smartregister.repository.AllSharedPreferences;
import org.smartregister.service.UserService;
import org.smartregister.shadows.LoginInteractorShadow;
import org.smartregister.shadows.ShadowNetworkUtils;
import org.smartregister.util.NetworkUtils;
import org.smartregister.view.contract.BaseLoginContract;

import java.lang.ref.WeakReference;
import java.util.Date;
import java.util.TimeZone;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Created by samuelgithengi on 8/4/20.
 */

public class BaseLoginInteractorTest extends BaseRobolectricUnitTest {

    @InjectMocks
    private LoginInteractorShadow interactor;

    @Mock
    private BaseLoginContract.Presenter presenter;

    @Mock
    private BaseLoginContract.View view;

    @Mock
    private Context context;

    @Mock
    private UserService userService;

    @Mock
    private AllSharedPreferences allSharedPreferences;

    @Mock
    private SyncConfiguration syncConfiguration;

    @Captor
    private ArgumentCaptor<DialogInterface.OnClickListener> dialogCaptor;

    @Mock
    private DialogInterface dialogInterface;

    @Mock
    private ResetAppHelper resetAppHelper;

    @Captor
    private ArgumentCaptor<OnCompleteClearDataCallback> onCompleteClearDataCaptor;

    private AppCompatActivity activity;

    private LoginResponseData loginResponseData;

    private String user = "johndoe";
    private String password = "qwerty";

    private UserService contextUserService;

    @Before
    public void setUp() {
        contextUserService = CoreLibrary.getInstance().context().userService();
        when(presenter.getOpenSRPContext()).thenReturn(context);
        when(context.allSharedPreferences()).thenReturn(allSharedPreferences);
        when(context.userService()).thenReturn(userService);
        when(presenter.getLoginView()).thenReturn(view);
        activity = Robolectric.buildActivity(AppCompatActivity.class).create().get();
        when(view.getActivityContext()).thenReturn(activity);
        loginResponseData = new LoginResponseData();
        loginResponseData.user = new User().withUsername(user);
        loginResponseData.time = new Time(new Date(), TimeZone.getTimeZone("Africa/Nairobi"));
    }

    @After
    public void tearDown() {
        Whitebox.setInternalState(CoreLibrary.getInstance().context(), "userService", contextUserService);
    }

    @Test
    public void testOnDestroyShouldSetPresenterNull() {
        assertNotNull(Whitebox.getInternalState(interactor, "mLoginPresenter"));
        interactor.onDestroy(false);
        assertNull(Whitebox.getInternalState(interactor, "mLoginPresenter"));
    }

    @Test
    public void testLoginAttemptsRemoteLoginAndErrorsWithBaseURLIsMissing() {
        when(allSharedPreferences.fetchBaseURL("")).thenReturn("");
        interactor.login(new WeakReference<>(view), "johndoe", "password");
        verify(view).hideKeyboard();
        verify(view).enableLoginButton(false);
        verify(allSharedPreferences).savePreference("DRISHTI_BASE_URL", activity.getString(R.string.opensrp_url));
        verify(view).enableLoginButton(true);
        verify(view).showErrorDialog(activity.getString(R.string.remote_login_base_url_missing_error));
        verify(view, never()).goToHome(anyBoolean());
    }

    @Test
    public void testLoginAttemptsRemoteLoginAndErrorsWithGenericError() {
        interactor.login(new WeakReference<>(view), "johndoe", "password");
        verify(view).hideKeyboard();
        verify(view).enableLoginButton(false);
        verify(allSharedPreferences, never()).savePreference("DRISHTI_BASE_URL", activity.getString(R.string.opensrp_url));
        verify(view, never()).enableLoginButton(true);
        verify(view).showErrorDialog(activity.getString(R.string.remote_login_generic_error));
        verify(view, never()).goToHome(anyBoolean());
    }


    @Test
    public void testLoginAttemptsRemoteLoginAndErrorsWhenNoInternetConnectivity() {
        Whitebox.setInternalState(CoreLibrary.getInstance().context(), "userService", userService);
        when(userService.isValidRemoteLogin(user, password)).thenReturn(LoginResponse.NO_INTERNET_CONNECTIVITY);
        when(allSharedPreferences.fetchBaseURL("")).thenReturn(activity.getString(R.string.opensrp_url));
        interactor.login(new WeakReference<>(view), user, password);
        verify(view).hideKeyboard();
        verify(view).enableLoginButton(false);
        verify(view).enableLoginButton(true);
        verify(view).showErrorDialog(activity.getString(R.string.no_internet_connectivity));
        verify(view, never()).goToHome(anyBoolean());
    }

    @Test
    public void testLoginAttemptsRemoteLoginAndErrorsWhenNullLoginResponse() {
        Whitebox.setInternalState(CoreLibrary.getInstance().context(), "userService", userService);
        when(userService.isValidRemoteLogin(user, password)).thenReturn(null);
        when(allSharedPreferences.fetchBaseURL("")).thenReturn(activity.getString(R.string.opensrp_url));
        interactor.login(new WeakReference<>(view), user, password);
        verify(view).hideKeyboard();
        verify(view).enableLoginButton(false);
        verify(view).enableLoginButton(true);
        verify(view).showErrorDialog(activity.getString(R.string.remote_login_generic_error));
        verify(view, never()).goToHome(anyBoolean());
    }


    @Test
    public void testLoginAttemptsRemoteLoginAndErrorsWhenResponseUnknown() {
        Whitebox.setInternalState(CoreLibrary.getInstance().context(), "userService", userService);
        when(userService.isValidRemoteLogin(user, password)).thenReturn(LoginResponse.UNKNOWN_RESPONSE);
        when(allSharedPreferences.fetchBaseURL("")).thenReturn(activity.getString(R.string.opensrp_url));
        interactor.login(new WeakReference<>(view), user, password);
        verify(view).hideKeyboard();
        verify(view).enableLoginButton(false);
        verify(view).enableLoginButton(true);
        verify(view).showErrorDialog(activity.getString(R.string.unknown_response));
        verify(view, never()).goToHome(anyBoolean());
    }

    @Test
    public void testLoginAttemptsRemoteLoginAndErrorsWhenUnauthorized() {
        Whitebox.setInternalState(CoreLibrary.getInstance().context(), "userService", userService);
        when(userService.isValidRemoteLogin(user, password)).thenReturn(LoginResponse.UNAUTHORIZED);
        when(allSharedPreferences.fetchBaseURL("")).thenReturn(activity.getString(R.string.opensrp_url));
        interactor.login(new WeakReference<>(view), user, password);
        verify(view).hideKeyboard();
        verify(view).enableLoginButton(false);
        verify(view).enableLoginButton(true);
        verify(view).showErrorDialog(activity.getString(R.string.unauthorized));
        verify(view, never()).goToHome(anyBoolean());
    }

    @Test
    public void testLoginAttemptsRemoteLoginAndErrorsWhenTimeIsWrong() {
        Whitebox.setInternalState(CoreLibrary.getInstance().context(), "userService", userService);
        when(userService.isValidRemoteLogin(user, password)).thenReturn(LoginResponse.SUCCESS.withPayload(loginResponseData));
        when(userService.validateDeviceTime(any(), anyLong())).thenReturn(TimeStatus.TIME_MISMATCH);
        when(userService.isUserInPioneerGroup(user)).thenReturn(true);
        when(allSharedPreferences.fetchBaseURL("")).thenReturn(activity.getString(R.string.opensrp_url));
        Whitebox.setInternalState(AllConstants.class, "TIME_CHECK", true);
        interactor.login(new WeakReference<>(view), user, password);
        verify(view).hideKeyboard();
        verify(view).enableLoginButton(false);
        verify(view).enableLoginButton(true);
        verify(view).showErrorDialog(activity.getString(TimeStatus.TIME_MISMATCH.getMessage()));
        verify(view, never()).goToHome(anyBoolean());
    }

    @Test
    public void testLoginAttemptsRemoteLoginAndErrorsWhenTimeZoneIsWrong() {
        Whitebox.setInternalState(CoreLibrary.getInstance().context(), "userService", userService);
        when(userService.isValidRemoteLogin(user, password)).thenReturn(LoginResponse.SUCCESS.withPayload(loginResponseData));
        when(userService.validateDeviceTime(any(), anyLong())).thenReturn(TimeStatus.TIMEZONE_MISMATCH);
        when(userService.isUserInPioneerGroup(user)).thenReturn(true);
        when(allSharedPreferences.fetchBaseURL("")).thenReturn(activity.getString(R.string.opensrp_url));
        Whitebox.setInternalState(AllConstants.class, "TIME_CHECK", true);
        interactor.login(new WeakReference<>(view), user, password);
        verify(view).hideKeyboard();
        verify(view).enableLoginButton(false);
        verify(view).enableLoginButton(true);
        verify(view).showErrorDialog(activity.getString(TimeStatus.TIMEZONE_MISMATCH.getMessage(), TimeZone.getTimeZone("Africa/Nairobi").getDisplayName()));
        verify(view, never()).goToHome(anyBoolean());
    }

    @Test
    @Config(shadows = {ShadowNetworkUtils.class})
    public void testLoginAttemptsRemoteLoginAndNavigatesToHome() {
        Whitebox.setInternalState(CoreLibrary.getInstance().context(), "userService", userService);
        when(userService.isValidRemoteLogin(user, password)).thenReturn(LoginResponse.SUCCESS.withPayload(loginResponseData));
        when(userService.validateDeviceTime(any(), anyLong())).thenReturn(TimeStatus.TIMEZONE_MISMATCH);
        when(userService.isUserInPioneerGroup(user)).thenReturn(true);
        when(allSharedPreferences.fetchBaseURL("")).thenReturn(activity.getString(R.string.opensrp_url));
        interactor.login(new WeakReference<>(view), user, password);
        verify(view).hideKeyboard();
        verify(view).enableLoginButton(false);
        verify(view).enableLoginButton(true);
        verify(userService).remoteLogin(user, password, loginResponseData);
        verify(view).goToHome(true);
    }


    @Test
    public void testLoginWithLocalFlagShouldAttemptRemoteLoginAndResetAppForNewUserAndStartsLogin() {
        Whitebox.setInternalState(CoreLibrary.getInstance().context(), "userService", userService);
        Whitebox.setInternalState(CoreLibrary.getInstance(), "syncConfiguration", syncConfiguration);
        Whitebox.setInternalState(interactor, "resetAppHelper", resetAppHelper);
        when(view.getAppCompatActivity()).thenReturn(activity);
        when(syncConfiguration.clearDataOnNewTeamLogin()).thenReturn(true);
        when(userService.isValidRemoteLogin(user, password)).thenReturn(LoginResponse.SUCCESS.withPayload(loginResponseData));
        when(userService.isUserInPioneerGroup(user)).thenReturn(false);
        when(allSharedPreferences.fetchBaseURL("")).thenReturn(activity.getString(R.string.opensrp_url));
        interactor = spy(interactor);
        interactor.loginWithLocalFlag(new WeakReference<>(view), false, user, password);
        verify(view).hideKeyboard();
        verify(view).enableLoginButton(false);
        verify(view).enableLoginButton(true);
        verify(view).showClearDataDialog(dialogCaptor.capture());
        dialogCaptor.getValue().onClick(dialogInterface, DialogInterface.BUTTON_POSITIVE);
        verify(dialogInterface).dismiss();
        verify(resetAppHelper).startResetProcess(eq(activity), onCompleteClearDataCaptor.capture());
        onCompleteClearDataCaptor.getValue().onComplete();
        verify(interactor).login(any(), eq(user), eq(password));

    }


}
