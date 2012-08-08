/*
 * Copyright (C) 2010 Pixelpod INTERNATIONAL, Inc.
 * 
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES
 * OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT
 * HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package com.cyanogenmod.cmappschanger;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.Arrays;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.text.ClipboardManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ListView;

/**
 * @author Timothy Caraballo
 * @version 0.9.3.5
 */
public class TypeAppFresh extends ListActivity {
    // Tag for Log use
    public static final String TAG = "CM Apps Changer";
    // activity requestCode
    public static final int PICK_REQUEST_CODE = 999;
    // menu
    public static final int MENU_APPLY   = 0;
    public static final int MENU_BACKUP  = 1;
    public static final int MENU_RESTORE = 2;
    public static final int MENU_RESET   = 3;
    // Dialogs
    public static final int DIALOG_NEED_REBOOT      =  101;
    public static final int DIALOG_REBOOT           =  102;
    public static final int DIALOG_REBOOT_FAILED    =  103;
    public static final int DIALOG_NEED_ROOT        =  104;
    public static final int DIALOG_MKDIR_FAILED     =  105;
    public static final int DIALOG_REMOUNT_FAILED   =  106;
    public static final int DIALOG_PROGRESS         =  107;
    public static final int PDIALOG_DISMISS         =  108;
    // ContextMenu selections
    public static final int CONTEXT_COPY  = 201;
    public static final int CONTEXT_PASTE = 202;
    public static final int CONTEXT_CLEAR = 203;
    
    private static String lastFolder = null;
    private String[] apps;
    private String[] sysAppPaths;
    private int listPosition;
    public ProgressDialog progressDialog = null;
    private static int progressDialogTitle = R.string.diag_copying;
    private AppListAdapter adapter = null;
    private static AsyncTask<Object, Object, Void> fileCopier = null;
    private static ClipboardManager clipboard = null;
    public static String extStorage = Environment.getExternalStorageDirectory().getPath();
    
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        
        File appsDir = new File("/system/app");
        apps = appsDir.list();
        // remove all file references for the sake of remounting
        appsDir = null;

        Arrays.sort(apps);
        sysAppPaths = new String[apps.length];
        
        File sdApps = new File(extStorage + "/SystemApps");
        if (!sdApps.exists()) {
            try {
                sdApps.mkdir();
            } catch (Exception e) {
                Log.e(TAG,e.toString());
                showDialog(DIALOG_MKDIR_FAILED);
            }
        }

        // build array of installed app paths
        for (int i = 0; i < apps.length; i++) {
            sysAppPaths[i] = "/system/app/" + apps[i];
        }
        
        setListAdapter(new AppListAdapter(this, apps));
        adapter = (AppListAdapter) this.getListAdapter();
        
        // restore paths on rotate
        if ((savedInstanceState != null) && savedInstanceState.containsKey("paths")) {
            adapter.setPaths(savedInstanceState.getStringArray("paths"));
        }

        if ((fileCopier != null) && (fileCopier.getStatus() != AsyncTask.Status.FINISHED)) {
            // we have a running thread, so tell it about our new activity
            ((FileCopier) fileCopier).setActivity(this);
            return;
        }

        // do we need to show the welcome screen?
        //SharedPreferences settings = getPreferences(Activity.MODE_PRIVATE);
        //if (settings.getBoolean("firstrun", true)) {
            // Not first run anymore, so store that
        //    SharedPreferences.Editor editor = settings.edit();
        //    editor.putBoolean("firstrun", false);
        //    editor.commit();
        //    showDialog(DIALOG_FIRSTRUN);
        //}
        
        registerForContextMenu(getListView());
    }

    @Override
    public void onSaveInstanceState(Bundle bundle) {
        // store the selected apps
        bundle.putStringArray("paths", adapter.appPaths);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_APPLY,   0, R.string.menu_apply);
        menu.add(0, MENU_BACKUP,  0, R.string.menu_backup);
        menu.add(0, MENU_RESTORE, 0, R.string.menu_restore).setIcon(android.R.drawable.ic_menu_revert);
        menu.add(0, MENU_RESET,   0, R.string.menu_reset).setIcon(R.drawable.ic_menu_clear_playlist);
        return true;
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // if the user hasn't selected any apps, the next two menu items are useless
        boolean pathsSet = !Arrays.equals(sysAppPaths, adapter.getPaths());
        menu.findItem(MENU_APPLY).setEnabled(pathsSet);
        menu.findItem(MENU_RESET).setEnabled(pathsSet);

        // Check for a backup to see if we should enable the restore option
        boolean backupExists = true;
        for (int i = 0; i < apps.length; i++) {
            // check if any existing apps are not backed up
            if (!(new File(extStorage + "/SystemApps/" + apps[i]).exists())) {
                backupExists = false;
                break;
            }
        }
        menu.findItem(MENU_RESTORE).setEnabled(backupExists);
        return true;
    }
    
    /* Handles Menu item selections */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_APPLY:
            applySelections();
            return true;
        case MENU_BACKUP:
            backupApps();
            return true;
        case MENU_RESTORE:
            restoreApps();
            return true;
        case MENU_RESET:
            resetSelections();
            return true;
        }
        return false;
    }
    
    @Override
    public void onListItemClick(ListView parent, View v, int position, long id) {
        listPosition = position; 
        Intent intent = new Intent("org.openintents.action.PICK_FILE");
        Uri startDir = null;

        if (lastFolder == null) {
            startDir = Uri.fromFile(new File(extStorage + "/SystemApps"));
        } else {
            startDir = Uri.fromFile(new File(lastFolder));
        }
        intent.setData(startDir);
        intent.putExtra("org.openintents.extra.TITLE", "Please select a APKs");
        
        try {
            startActivityForResult(intent, PICK_REQUEST_CODE);
        } catch (ActivityNotFoundException e) {
            Log.e(TAG, e.toString());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == PICK_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Uri uri = intent.getData();
                if (uri != null) {
                    String path = uri.toString();
                    if (path.toLowerCase().startsWith("file://")) {
                        File appFile = new File(URI.create(path)); 
                        path = appFile.getAbsolutePath();
                        adapter.setPathAt(listPosition, path);
                        lastFolder = appFile.getParent();
                    }
                }
            }
        }
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        menu.setHeaderTitle(apps[info.position]);

        // did the user set this path?
        if ( ! adapter.getPathAt(info.position).equals(sysAppPaths[info.position])) {
            menu.add(Menu.NONE, CONTEXT_COPY,  1, R.string.context_copy);
            menu.add(Menu.NONE, CONTEXT_CLEAR, 3, R.string.context_clear);
        }
        
        // is there a valid path in the clipboard?
        if (clipboard.hasText() && clipboard.getText().toString().matches("^/.+?.apk$")) {
            menu.add(Menu.NONE, CONTEXT_PASTE, 2, R.string.context_paste);
        }
    }
    
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();

        switch(item.getItemId()) {
        case CONTEXT_COPY:
            clipboard.setText( adapter.getPathAt(info.position));
            break;
        case CONTEXT_PASTE:
            adapter.setPathAt(info.position, clipboard.getText().toString());
            break;
        case CONTEXT_CLEAR:
            adapter.setPathAt(info.position, sysAppPaths[info.position]);
            break;
        default:
            super.onContextItemSelected(item);
        }

        return true;
    }
        
    /**
     * Copies all system apps to /sdcard/SystemApps
     */
    protected void backupApps() {
        String[] dPaths = new String[apps.length];
        for(int i = 0; i < apps.length; i++) {
            dPaths[i] = extStorage + "/SystemApps/" + apps[i];
        }

        copyFiles(R.string.diag_backing_up, R.string.toast_backed_up, sysAppPaths, dPaths);
    }

    /**
     * Restores backed up apps from /sdcard/SystemApps/
     */
    protected void restoreApps() {
        String[] sPaths = new String[apps.length];
        for(int i = 0; i < sPaths.length; i++) {
            sPaths[i] = extStorage + "/SystemApps/" + apps[i];
        }

        copyFiles(R.string.diag_restoring, R.string.toast_restored, sPaths, sysAppPaths);
        resetSelections();
    }
    
    /**
     * Resets all selected apps to empty.
     */
    protected void resetSelections() {
        adapter.setPaths(sysAppPaths);        
    }    

    /**
     * Initiates copying of selected apps to the system.
     */
    protected void applySelections() {
        String[] sPaths = adapter.getPaths();
        copyFiles( R.string.diag_applying, R.string.toast_applied, sPaths, sysAppPaths);
    }    

    /**
     * Copies files from each element in src to the corresponding dst.
     * 
     * @param dialogTitle    <code>int</code> String resource for the displayed
     *                          <code>ProgressDialog</code>'s title.
     * @param completedToast <code>int</code> String resource to show in <code>Toast</code> when
     *                          the process is done.
     * @param src            <code>String[]</code> of source paths.
     * @param dst            <code>String</code> of destination paths, same length as src.
     */
    protected void copyFiles(int dialogTitle, int completedToast,
                               String[] src, String[] dst) {
        if (src.length != dst.length) {
            Log.e(TAG,"copyApps: src and destination length mismatch. Quitting copy.");
            return;
        }
        
        progressDialogTitle = dialogTitle;
        fileCopier = new FileCopier(this);
        fileCopier.execute(src, dst, getString(completedToast));
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        AlertDialog dialog;

        switch (id) {
        case DIALOG_NEED_ROOT:
            dialog = makeSimpleAlertDialog(android.R.drawable.ic_dialog_alert,
                    R.string.need_root_message, R.string.need_root_title);
            break;
        case DIALOG_REBOOT_FAILED:
            dialog = makeSimpleAlertDialog(android.R.drawable.ic_dialog_alert,
                    R.string.reboot_failed_title, R.string.reboot_failed_message);
            break;
        case DIALOG_MKDIR_FAILED:
            dialog = makeSimpleAlertDialog(android.R.drawable.ic_dialog_alert,
                    R.string.mkdir_failed_title, R.string.mkdir_failed_message);
            break;
        case DIALOG_REMOUNT_FAILED:
            dialog = makeSimpleAlertDialog(android.R.drawable.ic_dialog_alert,
                    R.string.remount_failed_title, R.string.remount_failed_message);
            break;
        case DIALOG_NEED_REBOOT:
            dialog = (new AlertDialog.Builder(this))
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setMessage(R.string.reboot_message)
                .setTitle(R.string.reboot_title)
                .setPositiveButton(R.string.reboot_ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();

                        try {
                            reboot();
                        } catch (IOException e) {
                            Log.e(TAG, e.toString());
                            showDialog(TypeAppFresh.DIALOG_REBOOT_FAILED);
                        } catch (InterruptedException e) {
                            Log.e(TAG, e.toString());
                            showDialog(TypeAppFresh.DIALOG_REBOOT_FAILED);
                        }
                    }
                }
            ).create();
            break;
        case DIALOG_REBOOT:
            progressDialog = new ProgressDialog(this);
            progressDialog.setTitle(getString(R.string.diag_rebooting));
            progressDialog.setMessage(getString(R.string.please_wait));
            progressDialog.setCancelable(false);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            dialog = progressDialog;
            break;
        case DIALOG_PROGRESS:
            progressDialog = new ProgressDialog(this);
            progressDialog.setTitle(getString(progressDialogTitle));
            progressDialog.setCancelable(false);
            progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
            dialog = progressDialog;
            break;
        default:
            dialog = null;
        }
        
        return dialog;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        switch (id) {
        case DIALOG_PROGRESS:
            dialog.setTitle(progressDialogTitle);
            break;
        }
    }
    /**
     * Returns an AlertDialog with one dismiss button
     * 
     * @param icon <code>Drawable</code> resource id.
     * @param title <code>String</code> resource id.
     * @param message <code>String</code> resource id.
     * @return A fully built <code>AlertDialog</code>.
     */
    private AlertDialog makeSimpleAlertDialog(int icon, int title, int message) {
        return (new AlertDialog.Builder(this))
                .setIcon(icon)
                .setTitle(title)
                .setMessage(message)
                .setNeutralButton(R.string.dismiss, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                }
                ).create();
    }

    /**
     * Reboots the system.
     * 
     * @throws IOException If our <code>su</code> process has a problem.
     * @throws InterruptedException If our <code>su</code> process has a problem.
     */
    protected void reboot() throws IOException, InterruptedException {
        // it never actually shows the dialog, though :'/
        showDialog(DIALOG_REBOOT);

        if (fileCopier != null) {
            // there should be no way the FileCopier thread is still running, so just kill it
            fileCopier = null;
        }
        
        try {
            Log.i(TAG,"Calling reboot");
            Process su = FileCopier.getSu();
            DataOutputStream stream = new DataOutputStream(su.getOutputStream());
            stream.writeBytes("reboot\nexit\n");
            stream.flush();
            su.waitFor();
        } catch (IOException e) {
            // get rid of our dialog first and then throw the exception back
            dismissDialog(DIALOG_PROGRESS);
            throw e;
        } catch (InterruptedException e) {
            // get rid of our dialog first and then throw the exception back
            dismissDialog(DIALOG_PROGRESS);
            throw e;
        }
        
        if (progressDialog.isShowing()) {
            dismissDialog(DIALOG_PROGRESS);
        }
    }
}
