package com.afaneh.safestrap;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import com.topjohnwu.superuser.BusyBoxInstaller;
import com.topjohnwu.superuser.Shell;

public class SafestrapActivity extends AppCompatActivity {

    final static public String PREFS_NAME = "disclaimer";
    private TextView statusText = null;
    private TextView messageText = null;
    private TextView textSystemStatus = null;
    private Button buttonInstall = null;
    private Button buttonUninstall = null;
    private Button buttonReboot = null;
    private Button buttonRebootWriteProtect = null;
    private InstallDialogThread installDialogThread = null;
    private UninstallDialogThread uninstallDialogThread = null;
    private ProgressDialog pDialog = null;
    private Boolean rootCheck = false;
    private Boolean writeProtect = false;

    static {
        /* Configuration */
        Shell.Config.setFlags(Shell.FLAG_REDIRECT_STDERR);
        Shell.Config.verboseLogging(BuildConfig.DEBUG);
        /* Use internal busybox */
        Shell.Config.addInitializers(BusyBoxInstaller.class);
    }

    /*
     * Called when the activity is first created.
     */
    @SuppressLint("HandlerLeak")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        statusText = findViewById(R.id.textInstallStatus);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_safestrap);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        if (!settings.getBoolean("accepted", false)) {
            showDialog(0);
        }

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(view -> {
            AlertDialog.Builder dialog = new AlertDialog.Builder(SafestrapActivity.this);
            dialog.setCancelable(false);
            dialog.setIcon(R.mipmap.ic_launcher_foreground);
            dialog.setTitle(R.string.action_about_notice);
            dialog.setMessage(getString(R.string.what_is_safestrap) + "\n\n" + getString(R.string.special_thanks) + "\n\n" + getString(R.string.copyright_info));
            dialog.setPositiveButton("OK", (dialog1, id) -> {
                //Action for "OK".
            });
            dialog.setNeutralButton("Donate", (dialog1, id) -> {
                Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.paypal.me/mohammadafaneh"));
                startActivity(browserIntent);
            });


            final AlertDialog alert = dialog.create();
            alert.show();
        });

        buttonInstall = findViewById(R.id.buttonInstall);
        buttonUninstall = findViewById(R.id.buttonUninstall);
        buttonReboot = findViewById(R.id.buttonReboot);
        buttonRebootWriteProtect = findViewById(R.id.buttonRebootWriteProtect);
        statusText = findViewById(R.id.textInstallStatus);
        textSystemStatus = findViewById(R.id.textSystemStatus);

        rootCheck = Shell.rootAccess();
        if (rootCheck) {
            /* For new MotoX we need to check write_protect=1 in cmdline */
            AssetControl unzip = new AssetControl();
            unzip.apkPath = getPackageCodePath();
            unzip.mAppRoot = getFilesDir().toString();
            unzip.unzipAsset("/recovery-check.sh");
            unzip.unzipAsset("/recovery-reboot.sh");
            unzip.unzipAsset("/ss_function.sh");
            unzip.unzipAsset("/ss.config");
            Shell.su("chmod 755 " + getFilesDir() + "/*.sh").exec();

            buttonInstall.setOnClickListener(v -> {
                try {
                    pDialog = new ProgressDialog(v.getRootView().getContext());
                    pDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                    pDialog.setMessage("Installing...");
                    pDialog.setCancelable(false);
                    pDialog.show();
                    pDialog.getCurrentFocus();
                    installDialogThread = new InstallDialogThread();
                    installDialogThread.packageCodePath = getPackageCodePath();
                    installDialogThread.mAppRoot = getFilesDir();
                    installDialogThread.LOGTAG = "Safestrap";
                    installDialogThread.handler = new Handler() {
                        @Override
                        public void handleMessage(Message msg) {
                            if (msg.arg1 == 0) {
                                pDialog.dismiss();
                                pDialog = null;
                                Toast.makeText(buttonInstall.getRootView().getContext(), (String) msg.obj, Toast.LENGTH_LONG).show();
                                setupControls();
                            } else {
                                pDialog.setProgress(msg.arg2);
                                pDialog.setMessage((String) msg.obj);
                            }
                        }
                    };
                    installDialogThread.start();
                } catch (Exception ex) {
                    messageText.setText(ex.getMessage());
                }
            });

            buttonUninstall.setOnClickListener(v -> {
                try {
                    pDialog = new ProgressDialog(v.getRootView().getContext());
                    pDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
                    pDialog.setMessage("Loading...");
                    pDialog.setCancelable(false);
                    pDialog.show();
                    pDialog.getCurrentFocus();
                    uninstallDialogThread = new UninstallDialogThread();
                    uninstallDialogThread.packageCodePath = getPackageCodePath();
                    uninstallDialogThread.mAppRoot = getFilesDir();
                    uninstallDialogThread.LOGTAG = "Safestrap";
                    uninstallDialogThread.handler = new Handler() {
                        @Override
                        public void handleMessage(Message msg) {
                            if (msg.arg1 == 0) {
                                pDialog.dismiss();
                                pDialog = null;
                                Toast.makeText(buttonUninstall.getRootView().getContext(), (String) msg.obj, Toast.LENGTH_LONG).show();
                                setupControls();
                            } else {
                                pDialog.setProgress(msg.arg2);
                                pDialog.setMessage((String) msg.obj);
                            }
                        }
                    };
                    uninstallDialogThread.start();
                } catch (Exception ex) {
                    messageText.setText(ex.getMessage());
                }
            });

            buttonReboot.setOnClickListener(v -> {
                try {
                    Shell.su(getFilesDir().toString() + "/recovery-reboot.sh " + getFilesDir().toString()).exec();
                    Shell.su("reboot now").exec();
                } catch (Exception ex) {
                    messageText.setText(ex.getMessage());
                }
            });

            buttonRebootWriteProtect.setOnClickListener(v -> Shell.su("reboot recovery").exec());
        }
        setupControls();

    }

    /**
     * Setup the Widgets
     */
    protected void setupControls() {
        /* Setup button */
        buttonRebootWriteProtect.setVisibility(View.GONE);
        if (rootCheck) {
            /* Check Version */
            String inText = String.valueOf(Shell.su(getFilesDir().toString() + "/recovery-check.sh " + getFilesDir().toString()).exec().getOut()).replace("[", "").replace("]", "");
            String[] parts = inText.split(":");
            String vers_s = null;
            float vers = 0;
            boolean altbootmode = false;
            if (parts.length >= 1) {
                if(!parts[0].isEmpty())
                    vers_s = parts[0];
            }
            if (parts.length >= 2) {
                if (parts[1].equals("1")) altbootmode = true;
            }
            if (parts.length >= 3) {
                if (parts[2].equals("1")) {
                    writeProtect = true;
                }
            }
            if (vers_s != null) {
                vers = Float.valueOf(vers_s);
            }
            buttonUninstall.setEnabled(true);
            buttonInstall.setEnabled(true);
            buttonReboot.setEnabled(true);
            if (vers == 0) {
                statusText.setText(R.string.not_installed);
                buttonReboot.setEnabled(false);
            } else {
                float check_vers = Float.valueOf(this.getString(R.string.version_name));
                if (vers == check_vers) {
                    statusText.setText(R.string.installed);
                } else if (vers > check_vers) {
                    statusText.setText(R.string.newer_version_installed);
                    buttonInstall.setEnabled(false);
                } else {
                    statusText.setText(R.string.old_version);
                }
            }
            textSystemStatus.setText(R.string.not_active);
            /* setMessage(check); */
            if (altbootmode) {
                textSystemStatus.setText(R.string.active);
            }
            if (writeProtect) {
                statusText.setText(R.string.write_protect_enabled);
                buttonUninstall.setEnabled(false);
                buttonInstall.setEnabled(false);
                buttonReboot.setEnabled(true);
                buttonRebootWriteProtect.setVisibility(View.VISIBLE);
            }
        } else {
            statusText.setText(R.string.not_rooted);
            buttonUninstall.setEnabled(false);
            buttonInstall.setEnabled(false);
            buttonReboot.setEnabled(false);
        }
    }

    /*
     * Show the disclaimer....
     */
    protected Dialog onCreateDialog(int id) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(R.mipmap.ic_launcher_foreground);
        builder.setTitle(R.string.dialog_disclaimer_title);
        builder.setMessage(R.string.disclaimer);
        builder.setCancelable(false);
        builder.setPositiveButton("Agree", (dialog, id1) -> {
            /*
             * and, if the user accept, you can execute something like this:
             * We need an Editor object to make preference changes.
             * All objects are from android.context.Context
             */
            SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
            SharedPreferences.Editor editor = settings.edit();
            editor.putBoolean("accepted", true);
            /* Commit the edits! */
            editor.apply();
        }).setNegativeButton("Disagree", (dialog, id12) -> System.exit(0));
        return builder.create();
    }
}