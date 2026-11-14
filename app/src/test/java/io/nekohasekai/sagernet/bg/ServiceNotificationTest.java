package io.nekohasekai.sagernet.bg;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.pm.ServiceInfo;
import io.nekohasekai.sagernet.SagerNet;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowService;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {34, 35}, application = Application.class, manifest = Config.NONE)
public class ServiceNotificationTest {
    @Before public void setup() {
        SagerNet app = new SagerNet();
        ReflectionHelpers.callInstanceMethod(app,"attach",ReflectionHelpers.ClassParameter.from(
                Context.class,RuntimeEnvironment.getApplication()));
        SagerNet.Companion.setApplication(app);
        NotificationManager manager = app.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel("service-proxy","Proxy",NotificationManager.IMPORTANCE_LOW));
        manager.createNotificationChannel(new NotificationChannel("service-vpn","VPN",NotificationManager.IMPORTANCE_LOW));
    }

    @Test public void proxyUsesSpecialUseWhileVpnUsesSystemExemption() {
        ProxyService proxy = Robolectric.buildService(ProxyService.class).create().get();
        ServiceNotification proxyNotification = proxy.createNotification("fixture");
        try { assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,proxy.getForegroundServiceType()); }
        finally { proxyNotification.destroy(); }
        VpnService vpn = Robolectric.buildService(VpnService.class).create().get();
        ServiceNotification vpnNotification = vpn.createNotification("fixture");
        try { assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED,vpn.getForegroundServiceType()); }
        finally { vpnNotification.destroy(); }
    }

    @Implements(Service.class)
    public static class RejectForeground extends ShadowService {
        @Implementation protected void startForeground(int id, Notification notification, int type) {
            throw new SecurityException("Foreground denied");
        }
    }

    @Test @Config(shadows = RejectForeground.class)
    public void foregroundFailureReachesTheStartupHandler() {
        ProxyService service = Robolectric.buildService(ProxyService.class).create().get();
        try { service.createNotification("fixture"); fail("foreground failure was swallowed"); }
        catch (SecurityException expected) { assertEquals("Foreground denied",expected.getMessage()); }
    }
}
