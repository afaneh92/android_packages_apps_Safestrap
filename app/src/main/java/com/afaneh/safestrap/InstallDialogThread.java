package com.afaneh.safestrap;

import android.os.Handler;
import android.os.Message;

import java.io.File;
import com.topjohnwu.superuser.Shell;

public class InstallDialogThread extends Thread {
    Handler handler = null;
    String packageCodePath = "";
    File mAppRoot = null;
    String LOGTAG = "";

    private void pause(int milli) {
        try {
            Thread.sleep(milli);
        } catch (Exception ignored) {
        }
    }

    private void reply(int arg1, int arg2, String text) {
        Message msg = new Message();
        msg.arg1 = arg1;
        msg.arg2 = arg2;
        msg.obj = text;
        if (handler != null) {
            handler.sendMessage(msg);
        }
    }

    @Override
    public void run() {
        try {
            reply(1, 0, "Preparing Installation...");
            pause(2000);
            AssetControl unzip = new AssetControl();
            unzip.apkPath = packageCodePath;
            unzip.mAppRoot = mAppRoot.toString();
            unzip.LOGTAG = LOGTAG;
            reply(1, 0, "Unpacking Files...");
            unzip.unzipAssets();
            reply(1, 50, "Checking Files...");
            String filesDir = mAppRoot.getAbsolutePath();
            Shell.su("chmod 755 " + filesDir + "/*.sh").exec();
            reply(1, 60, "Running Installation Script...");
            Shell.su(filesDir + "/recovery-install.sh " + filesDir).exec();
            reply(1, 90, "Cleaning Up...");
            Shell.su(filesDir + "rm -r " + filesDir + "/install-files").exec();
            pause(1000);
            reply(0, 0, "Installation Complete.");
        } catch (Exception ex) {
            reply(0, 1, ex.getMessage());
        }
    }
}