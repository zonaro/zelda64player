package br.com.redclaw.zelda64player.retroachievements.jni;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** Real JNI/rcheevos integration with synthetic memory and an entirely fake server. */
public final class RcheevosJni {
    static { System.load(System.getProperty("ra.library")); }
    static native void nativeCreateClient(Object listener);
    static native void nativeDestroyClient();
    static native void nativeBeginLoginWithToken(String user, String token, int op);
    static native void nativeIdentifyAndLoadGame(String file, int op);
    static native void nativeSetMemoryRegion(ByteBuffer memory);
    static native void nativeDoFrame();
    static native void nativeIdle();
    static native byte[] nativeSerializeProgress();
    static native boolean nativeDeserializeProgress(byte[] data);
    static native void nativeResetProgress();
    static native boolean nativeIsHardcore();
    static native void nativeSetHardcoreEnabled(boolean enabled);
    static native String[] nativeBuildAwardRequest(String username, String token, long id, boolean hardcore, String hash, long seconds);
    static native void nativeCompleteServerRequest(int id, int status, byte[] body, String error);
    static native String nativeGetGameInfoJson();
    static native String nativeGetAchievementListJson();

    private record Request(int id, String data) {}
    private final ArrayDeque<Request> pending = new ArrayDeque<>();
    private final Map<Integer, Integer> operations = new HashMap<>();
    private int unlockEvents;
    private int awardAttempts;
    private String awardData;
    private boolean alreadyUnlocked;
    private boolean progressCase;

    public void onServerRequest(int id, String url, String data) {
        // Deliberately never use URL or a network client: all server replies are synthetic.
        pending.add(new Request(id, data));
    }
    public void onAsyncResult(int op, int code, String error) {
        check(code == 0, "native operation " + op + " failed: " + error);
        operations.put(op, code);
    }
    public void onClientEvent(int event, String json) {
        if (event == 1) {
            check(json.contains("\"id\":42"), "wrong achievement event");
            unlockEvents++;
        }
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    private void drain() {
        while (!pending.isEmpty()) {
            Request request = pending.remove();
            String reply;
            if (request.data.contains("r=login2")) {
                reply = "{\"Success\":true,\"User\":\"synthetic-user\",\"Token\":\"synthetic-token\",\"Score\":0}";
            } else if (request.data.contains("r=gameid")) {
                reply = "{\"Success\":true,\"GameID\":123}";
            } else if (request.data.contains("r=achievementsets")) {
                reply = """
                    {"Success":true,"GameId":123,"Title":"Synthetic test","ConsoleId":2,
                     "ImageIconUrl":"https://example.test/1.png","RichPresencePatch":"",
                     "Sets":[{"AchievementSetId":123,"GameId":123,"Title":"Synthetic test","Type":"core",
                     "ImageIconUrl":"https://example.test/1.png","Achievements":[
                     {"ID":42,"Title":"Synthetic award","Description":"Set byte to one","Flags":3,
                      "Points":5,"MemAddr":"0xH000001=1","Author":"test","BadgeName":"1","Created":0,"Modified":0}],
                     "Leaderboards":[]}]}
                    """;
            } else if (request.data.contains("r=startsession")) {
                reply = alreadyUnlocked
                    ? "{\"Success\":true,\"Unlocks\":[{\"ID\":42,\"When\":1}],\"HardcoreUnlocks\":[]}"
                    : "{\"Success\":true,\"Unlocks\":[],\"HardcoreUnlocks\":[]}";
            } else if (request.data.contains("r=awardachievement")) {
                awardData = request.data;
                awardAttempts++;
                if (awardAttempts <= 2) {
                    nativeCompleteServerRequest(request.id, 0, null, "synthetic disconnect");
                    continue;
                }
                reply = "{\"Success\":true,\"Score\":0,\"SoftcoreScore\":5,\"AchievementID\":42,\"AchievementsRemaining\":0}";
            } else if (request.data.contains("r=ping")) {
                reply = "{\"Success\":true}";
            } else {
                throw new AssertionError("unexpected synthetic request");
            }
            if (progressCase) reply = reply.replace("0xH000001=1", "0xH000001=1.3.");
            nativeCompleteServerRequest(request.id, 200, reply.getBytes(StandardCharsets.UTF_8), null);
        }
    }
    private void start(Path rom, ByteBuffer memory) {
        nativeCreateClient(this);
        nativeBeginLoginWithToken("synthetic-user", "synthetic-token", 1);
        drain();
        check(operations.containsKey(1), "login not completed");
        nativeSetMemoryRegion(memory);
        nativeIdentifyAndLoadGame(rom.toString(), 2);
        drain();
        nativeDoFrame();
        drain();
        check(operations.containsKey(2), "load not completed");
    }
    public static void main(String[] args) throws Exception {
        Path rom = Path.of(args[0]);
        byte[] data = new byte[4096];
        data[0] = (byte)0x80; data[1] = 0x37; data[2] = 0x12; data[3] = 0x40;
        Files.write(rom, data);
        ByteBuffer memory = ByteBuffer.allocateDirect(8 * 1024 * 1024);
        RcheevosJni test = new RcheevosJni();
        try {
            test.start(rom, memory);
            for (int i = 0; i < 3; i++) nativeDoFrame();
            check(test.unlockEvents == 0, "unmet condition unlocked");
            memory.put(1, (byte)1);
            nativeDoFrame(); test.drain();
            check(test.unlockEvents == 1, "condition did not unlock exactly once");
            check(test.awardAttempts == 2, "initial failure was not retried immediately");
            check(test.awardData.contains("a=42") && test.awardData.contains("h=0"), "wrong softcore award");
            check(nativeGetGameInfoJson().contains("\"num_unlocked_achievements\":1"), "summary is stale");
            check(nativeGetAchievementListJson().contains("\"unlocked\":1"), "list is stale");
            Thread.sleep(1200);
            nativeIdle(); test.drain();
            check(test.awardAttempts == 3, "paused idle failed to resend pending award");
            for (int i = 0; i < 5; i++) nativeDoFrame();
            test.drain();
            check(test.unlockEvents == 1 && test.awardAttempts == 3, "award duplicated");
            nativeSetMemoryRegion(null); nativeDoFrame(); nativeIdle();
        } finally { nativeDestroyClient(); }
        RcheevosJni restored = new RcheevosJni();
        restored.alreadyUnlocked = true;
        try {
            restored.start(rom, memory);
            for (int i = 0; i < 5; i++) nativeDoFrame();
            restored.drain();
            check(restored.unlockEvents == 0 && restored.awardAttempts == 0, "server unlock was re-awarded");
        } finally { nativeDestroyClient(); }
        memory.put(1, (byte)0);
        RcheevosJni progress = new RcheevosJni();
        progress.progressCase = true;
        try {
            progress.start(rom, memory);
            nativeDoFrame();
            memory.put(1, (byte)1);
            nativeDoFrame(); // first hit
            byte[] saved = nativeSerializeProgress();
            check(saved != null && saved.length > 0, "progress was not serialized");
            nativeDoFrame(); // second hit
            check(nativeDeserializeProgress(saved), "progress restore failed");
            nativeDoFrame(); // restored second hit
            check(progress.unlockEvents == 0, "restore retained future hit count");
            nativeResetProgress();
            memory.put(1, (byte)0); nativeDoFrame();
            memory.put(1, (byte)1); nativeDoFrame(); nativeDoFrame();
            check(progress.unlockEvents == 0, "reset retained hit count");
            nativeDoFrame();
            check(progress.unlockEvents == 1, "restored/reset trigger failed to unlock");
            // Leave award HTTP in flight, then tear down. Its late completion must be ignored safely.
            Request inFlight = progress.pending.remove();
            nativeDestroyClient();
            nativeCompleteServerRequest(inFlight.id, 200, "{\"Success\":true}".getBytes(StandardCharsets.UTF_8), null);
        } finally { nativeDestroyClient(); }
        nativeCreateClient(new RcheevosJni());
        try {
            nativeSetHardcoreEnabled(true);
            check(nativeIsHardcore() && nativeSerializeProgress() == null, "hardcore capture gate failed");
        } finally { nativeDestroyClient(); }
        String[] replay = nativeBuildAwardRequest("synthetic-user", "synthetic-token", 42, false,
            "0123456789abcdef0123456789abcdef", 120);
        check(replay != null && replay[1].contains("o=120") && replay[1].contains("h=0") && replay[1].contains("v="),
            "replay did not sign original mode and elapsed time");
        System.out.println("PASS: real JNI unlock, softcore submission, retry while paused, summary/list, deduplication, restored unlocks, detached memory, save-state hit counts, reset, late callback, replay signing");
    }
}
