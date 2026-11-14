package io.nekohasekai.sagernet;

import android.app.Application;
import android.content.Intent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class, manifest = Config.NONE)
public class BootReceiverTest {
    @Test public void unrelatedExplicitIntentsAreIgnoredBeforeReadingSettings() {
        Application context = RuntimeEnvironment.getApplication();
        BootReceiver receiver = new BootReceiver();
        receiver.onReceive(context,new Intent("other.app.START").setClass(context,BootReceiver.class));
        receiver.onReceive(context,new Intent().setClass(context,BootReceiver.class));
        assertNull(shadowOf(context).getNextStartedService());
    }
}
