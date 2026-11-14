package io.nekohasekai.sagernet.database;

import android.app.Application;
import android.database.sqlite.SQLiteDatabase;
import android.os.Parcel;
import androidx.room.Room;
import org.json.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {21, 28}, application = Application.class, manifest = Config.NONE)
public class RemoteRuleSetTest {
    private static String readText(Path path) throws IOException { return new String(Files.readAllBytes(path), java.nio.charset.StandardCharsets.UTF_8); }
    private final RemoteRuleSetStore store = RemoteRuleSetStore.INSTANCE;
    private RemoteRuleSetEntity entity(long id, String tag) {
        return new RemoteRuleSetEntity(id, "Fixture", tag, "https://example.invalid/rules.json", "source", 1440, true);
    }

    @Test public void roomUpgradePreservesOldRowsAndSupportsCrud() throws Exception {
        Application context = RuntimeEnvironment.getApplication();
        File file = context.getDatabasePath("migration-test");
        file.getParentFile().mkdirs();
        JSONObject schema = new JSONObject(readText(Paths.get(System.getProperty("phase2.schemaDir"), "6.json"))).getJSONObject("database");
        SQLiteDatabase legacy = SQLiteDatabase.openOrCreateDatabase(file, null);
        JSONArray tables = schema.getJSONArray("entities");
        for (int i = 0; i < tables.length(); i++) {
            JSONObject table = tables.getJSONObject(i);
            legacy.execSQL(table.getString("createSql").replace("${TABLE_NAME}", table.getString("tableName")));
            JSONArray indices = table.getJSONArray("indices");
            for (int j = 0; j < indices.length(); j++) legacy.execSQL(indices.getJSONObject(j).getString("createSql").replace("${TABLE_NAME}", table.getString("tableName")));
        }
        for (int i = 0; i < schema.getJSONArray("setupQueries").length(); i++) legacy.execSQL(schema.getJSONArray("setupQueries").getString(i));
        legacy.execSQL("INSERT INTO rules (id,name,config,userOrder,enabled,domains,ip,port,sourcePort,network,source,protocol,outbound,packages) VALUES (42,'existing','',7,1,'example.invalid','','','','','','',-1,'existing.app')");
        legacy.execSQL("INSERT INTO proxy_groups (id,userOrder,ungrouped,name,type,subscription,`order`,isSelector,frontProxy,landingProxy) VALUES (9,3,0,'Existing group',0,NULL,0,1,-1,-1)");
        io.nekohasekai.sagernet.fmt.socks.SOCKSBean bean = new io.nekohasekai.sagernet.fmt.socks.SOCKSBean();
        bean.initializeDefaultValues(); bean.name="Existing node"; bean.serverAddress="example.invalid"; bean.serverPort=1080;
        byte[] encoded = io.nekohasekai.sagernet.fmt.KryoConverters.serialize(bean);
        legacy.execSQL("INSERT INTO proxy_entities (id,groupId,type,userOrder,tx,rx,status,ping,uuid,socksBean) VALUES (11,9,0,4,123,456,0,50,'fixture-id',?)", new Object[]{encoded});
        legacy.setVersion(6); legacy.close();
        SagerDatabase db = Room.databaseBuilder(context, SagerDatabase.class, "migration-test").allowMainThreadQueries().build();
        try {
            RuleEntity old = db.rulesDao().getById(42);
            assertNotNull(old); assertEquals("existing", old.getName()); assertEquals("example.invalid", old.getDomains());
            assertEquals(Collections.singleton("existing.app"), old.getPackages()); assertEquals("", old.getRemoteRuleSetTags());
            assertEquals(7, db.getOpenHelper().getWritableDatabase().getVersion());
            assertEquals("Existing group",db.groupDao().getById(9).getName());
            assertTrue(db.groupDao().getById(9).isSelector());
            assertEquals("Existing node",db.proxyDao().getById(11).getSocksBean().name);
            assertEquals(123,db.proxyDao().getById(11).getTx()); assertEquals(456,db.proxyDao().getById(11).getRx());
            RemoteRuleSetEntity first = entity(0, "first"); first.setId(db.remoteRuleSetsDao().insert(first));
            RemoteRuleSetEntity second = entity(0, "second"); second.setId(db.remoteRuleSetsDao().insert(second));
            assertEquals(2, db.remoteRuleSetsDao().all().size());
            first.setEnabled(false); db.remoteRuleSetsDao().update(first);
            assertFalse(db.remoteRuleSetsDao().get(first.getId()).getEnabled());
            first.setEnabled(true); db.remoteRuleSetsDao().update(first);
            assertTrue(db.remoteRuleSetsDao().get(first.getId()).getEnabled());
            try { db.remoteRuleSetsDao().insert(entity(0, "first")); fail("duplicate tag"); } catch (android.database.sqlite.SQLiteConstraintException expected) { }
            old.setRemoteRuleSetTags("first\nsecond"); db.rulesDao().updateRule(old);
            assertEquals("first\nsecond", db.rulesDao().getById(42).getRemoteRuleSetTags());
            db.remoteRuleSetsDao().delete(second.getId()); assertNull(db.remoteRuleSetsDao().get(second.getId()));
            assertEquals("existing", db.rulesDao().getById(42).getName());
        } finally { db.close(); }
    }

    @Test public void oldParcelEncodingIsUnchangedAndOptionalBackupRoundTrips() throws Exception {
        RuleEntity original = new RuleEntity(); original.setId(42); original.setName("legacy");
        original.setDomains("example.invalid"); original.setPackages(Collections.singleton("existing.app"));
        Parcel before = Parcel.obtain(); original.writeToParcel(before, 0); byte[] old = before.marshall(); before.recycle();
        original.setRemoteRuleSetTags("first\nsecond");
        Parcel after = Parcel.obtain(); original.writeToParcel(after, 0); assertArrayEquals(old, after.marshall());
        after.setDataPosition(0); RuleEntity restored = ParcelizeBridge.createRule(after); after.recycle();
        assertEquals("legacy", restored.getName()); assertEquals("", restored.getRemoteRuleSetTags());
        List<RemoteRuleSetEntity> sets = Arrays.asList(entity(1,"first"), entity(2,"second"));
        assertEquals(sets, store.parse(new JSONArray(store.export(sets).toString())));
        assertTrue(store.parse(null).isEmpty());
        JSONArray invalid = store.export(Arrays.asList(entity(1,"same"), entity(2,"same")));
        try { store.parse(invalid); fail("duplicate backup tag"); } catch (IllegalArgumentException expected) { }
        store.validateReferences(sets, Collections.singletonList(original));
        original.setEnabled(true); sets.get(0).setEnabled(false);
        try { store.validateReferences(sets, Collections.singletonList(original)); fail("disabled reference"); } catch (IllegalArgumentException expected) { }
        original.setRemoteRuleSetTags("missing");
        try { store.validateReferences(sets, Collections.singletonList(original)); fail("missing reference"); } catch (IllegalStateException expected) { }
    }

    @Test public void actualBackupV1ImportAndNewBackupRoundTrip() throws Exception {
        io.nekohasekai.sagernet.SagerNet application = new io.nekohasekai.sagernet.SagerNet();
        org.robolectric.util.ReflectionHelpers.callInstanceMethod(application,"attach",
            org.robolectric.util.ReflectionHelpers.ClassParameter.from(android.content.Context.class,RuntimeEnvironment.getApplication()));
        io.nekohasekai.sagernet.SagerNet.Companion.setApplication(application);
        androidx.work.WorkManager.initialize(application,new androidx.work.Configuration.Builder().build());
        SagerDatabase db = SagerDatabase.Companion.getInstance();
        io.nekohasekai.sagernet.ui.BackupFragment backup = new io.nekohasekai.sagernet.ui.BackupFragment();
        try {
            RuleEntity rule = new RuleEntity(); rule.setId(42); rule.setName("old backup rule"); rule.setDomains("example.invalid");
            db.rulesDao().createRule(rule);
            JSONObject old = new JSONObject(backup.doBackup(false,true,false));
            old.remove("remoteRuleSets"); old.remove("remoteRuleSetReferences");
            backup.finishImport(old,false,true,false);
            assertEquals("old backup rule",db.rulesDao().getById(42).getName());
            assertTrue(db.remoteRuleSetsDao().all().isEmpty());
            RemoteRuleSetEntity e = entity(1,"first"); e.setEnabled(false);
            db.remoteRuleSetsDao().insert(e);
            rule.setRemoteRuleSetTags("first"); db.rulesDao().updateRule(rule);
            JSONObject exported = new JSONObject(backup.doBackup(false,true,false));
            assertEquals(1,exported.getInt("version"));
            backup.finishImport(exported,false,true,false);
            assertEquals(e,db.remoteRuleSetsDao().get(1));
            assertEquals("first",db.rulesDao().getById(42).getRemoteRuleSetTags());
            assertEquals(old.getJSONArray("rules").getString(0),exported.getJSONArray("rules").getString(0));
            JSONObject bad = new JSONObject(exported.toString());
            bad.getJSONArray("remoteRuleSets").getJSONObject(0).put("format","invalid");
            try { backup.finishImport(bad,false,true,false); fail("invalid block"); } catch (IllegalArgumentException expected) { }
            assertEquals(e,db.remoteRuleSetsDao().get(1)); assertEquals("first",db.rulesDao().getById(42).getRemoteRuleSetTags());
            JSONObject malformed = new JSONObject(exported.toString());
            malformed.getJSONArray("remoteRuleSets").getJSONObject(0).put("id",new JSONObject().put("url","https://example.invalid/?token=FAKE_TEST_TOKEN"));
            try { backup.finishImport(malformed,false,true,false); fail("malformed ID"); }
            catch (IllegalArgumentException expected) {
                assertFalse(expected.toString().contains("FAKE_TEST_TOKEN")); assertNull(expected.getCause());
            }
            try { RemoteRuleSetManager.INSTANCE.delete(1); fail("referenced delete"); } catch (IllegalArgumentException expected) { }
            try { RemoteRuleSetManager.INSTANCE.save(new RemoteRuleSetEntity(1,"Fixture","renamed",e.getUrl(),"source",1440,false)); fail("referenced rename"); } catch (IllegalArgumentException expected) { }
            rule.setRemoteRuleSetTags(""); db.rulesDao().updateRule(rule);
            File cache = new File(application.getFilesDir(),"remote-rule-sets/1.source"); cache.getParentFile().mkdirs(); Files.write(cache.toPath(),"valid".getBytes());
            assertTrue(RemoteRuleSetManager.INSTANCE.config(Collections.emptyList()).isEmpty());
            e.setEnabled(true); db.remoteRuleSetsDao().update(e);
            List<moe.matsuri.nb4a.SingBoxOptions.RuleSet> options = RemoteRuleSetManager.INSTANCE.config(Collections.emptyList());
            assertEquals(1,options.size()); assertEquals("local",options.get(0).type);
            assertEquals("first",options.get(0).tag); assertEquals("source",options.get(0).format);
            assertEquals(cache.getAbsolutePath(),options.get(0).path); assertNull(options.get(0).url);
            rule.setEnabled(true); rule.setRemoteRuleSetTags("first");
            e.setEnabled(false); db.remoteRuleSetsDao().update(e);
            try { RemoteRuleSetManager.INSTANCE.config(Collections.singletonList(rule)); fail("disabled reference config"); } catch (IllegalArgumentException expected) { }
            rule.setRemoteRuleSetTags("missing");
            try { RemoteRuleSetManager.INSTANCE.config(Collections.singletonList(rule)); fail("missing reference config"); } catch (IllegalStateException expected) { }
            RemoteRuleSetManager.INSTANCE.delete(1);
            assertNull(db.remoteRuleSetsDao().get(1)); assertFalse(cache.exists());
        } finally { db.close(); }
    }

    @Test public void atomicReplacementKeepsPreviousCacheOnEveryFailure() throws Exception {
        File directory = Files.createTempDirectory("rule-set-test").toFile();
        File target = new File(directory,"1.source"); Files.write(target.toPath(),"old".getBytes());
        kotlin.jvm.functions.Function2<File,File,kotlin.Unit> rename = (from,to) -> {
            try { Files.move(from.toPath(),to.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); return kotlin.Unit.INSTANCE; }
            catch (IOException e) { throw new RuntimeException(e); }
        };
        try {
            store.replace(target,new ByteArrayInputStream("bad".getBytes()), file -> { throw new IllegalArgumentException("invalid"); },rename);
            fail("invalid content");
        } catch (IllegalArgumentException expected) { }
        assertEquals("old", readText(target.toPath()));
        try {
            store.replace(target,new InputStream() { public int read() throws IOException { throw new IOException("network"); } }, file -> kotlin.Unit.INSTANCE,rename);
            fail("network failure");
        } catch (Exception expected) { assertTrue(expected instanceof IOException); }
        assertEquals("old", readText(target.toPath()));
        try {
            store.replace(target,new ByteArrayInputStream("new".getBytes()), file -> kotlin.Unit.INSTANCE,(a,b) -> { throw new IllegalStateException("rename failed"); });
            fail("rename failure");
        } catch (IllegalStateException expected) { }
        assertEquals("old", readText(target.toPath()));
        AtomicBoolean stop = new AtomicBoolean(); AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread reader = new Thread(() -> { while (!stop.get()) try {
            String value = readText(target.toPath());
            if (!value.equals("old") && !value.equals("new")) throw new AssertionError(value);
        } catch (Throwable error) { failure.set(error); break; } }); reader.start();
        int completed = 0, sharingDenials = 0;
        try {
            for (int i=0;i<100;i++) try {
                store.replace(target,new ByteArrayInputStream((i%2==0?"new":"old").getBytes()),file -> kotlin.Unit.INSTANCE,rename);
                completed++;
            } catch (RuntimeException e) {
                // Windows may reject replacing an open file. The production Android implementation
                // uses POSIX rename; verify refusal is safe here, without adding retries to production.
                if (!System.getProperty("os.name").startsWith("Windows") || !(e.getCause() instanceof AccessDeniedException)) throw e;
                sharingDenials++;
            }
        } finally { stop.set(true); reader.join(3000); }
        assertFalse(reader.isAlive()); assertNull(failure.get());
        System.out.println("Concurrent replacements="+completed+", Windows sharing refusals="+sharingDenials);
        store.replace(target,new ByteArrayInputStream("new".getBytes()),file -> kotlin.Unit.INSTANCE,rename);
        assertEquals("new",readText(target.toPath()));
        assertEquals(1, Objects.requireNonNull(directory.list()).length);
    }

    @Test public void realHttpSuccessFailureAndInvalidContentRetainCache() throws Exception {
        File directory = Files.createTempDirectory("rule-set-http").toFile();
        File target = new File(directory,"1.source"); Files.write(target.toPath(),"old".getBytes());
        java.net.ServerSocket server = new java.net.ServerSocket(0, 10, java.net.InetAddress.getByName("127.0.0.1"));
        AtomicReference<Throwable> serverFailure = new AtomicReference<>();
        Thread replies = new Thread(() -> {
            try { for (int i=0;i<3;i++) try (java.net.Socket socket = server.accept()) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                while (!reader.readLine().isEmpty()) { }
                String body = i == 0 ? "valid" : "invalid";
                String status = i == 1 ? "503 Unavailable" : "200 OK";
                socket.getOutputStream().write(("HTTP/1.1 "+status+"\r\nContent-Length: "+body.length()+"\r\nConnection: close\r\n\r\n"+body).getBytes());
                socket.getOutputStream().flush(); socket.shutdownOutput(); socket.setSoTimeout(3000);
                while (reader.read() != -1) { }
            } } catch (IOException e) { if (!server.isClosed()) serverFailure.set(e); }
        }); replies.start();
        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient.Builder().proxy(java.net.Proxy.NO_PROXY).callTimeout(3, TimeUnit.SECONDS).build();
        String url = "http://127.0.0.1:"+server.getLocalPort()+"/fixture";
        kotlin.jvm.functions.Function1<File,kotlin.Unit> validate = file -> {
            try { if (!readText(file.toPath()).equals("valid")) throw new IllegalArgumentException("invalid"); }
            catch (IOException e) { throw new RuntimeException(e); }
            return kotlin.Unit.INSTANCE;
        };
        kotlin.jvm.functions.Function2<File,File,kotlin.Unit> rename = (from,to) -> {
            try { Files.move(from.toPath(),to.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); return kotlin.Unit.INSTANCE; }
            catch (IOException e) { throw new RuntimeException(e); }
        };
        try {
            RemoteRuleSetDownload.INSTANCE.fetch(client,url,target,validate,rename);
            assertEquals("valid",readText(target.toPath()));
            try { RemoteRuleSetDownload.INSTANCE.fetch(client,url,target,validate,rename); fail("HTTP failure"); }
            catch (Exception e) { assertEquals("HTTP 503",e.getMessage()); }
            try { RemoteRuleSetDownload.INSTANCE.fetch(client,url,target,validate,rename); fail("invalid content"); }
            catch (IllegalArgumentException expected) { }
            assertEquals("valid",readText(target.toPath()));
            server.close(); replies.join(3000); assertFalse(replies.isAlive());
            assertNull(serverFailure.get());
            try { RemoteRuleSetDownload.INSTANCE.fetch(client,url,target,validate,rename); fail("network failure"); }
            catch (Exception expected) { assertTrue(expected instanceof IOException); }
            assertEquals("valid",readText(target.toPath()));
            assertEquals(1,Objects.requireNonNull(directory.list()).length);
        } finally { server.close(); client.connectionPool().evictAll(); client.dispatcher().executorService().shutdownNow(); replies.join(3000); }
    }
}
