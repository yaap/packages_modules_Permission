/*
 * Copyright (C) 2022 GrapheneOS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.permissioncontroller.sscopes;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.app.StorageScope;
import android.app.compat.gms.GmsCompat;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.GosPackageState;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Process;
import android.os.UserHandle;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;

import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.ui.handheld.SettingsWithLargeHeader;
import com.android.permissioncontroller.permission.utils.KotlinUtils;
import com.android.settingslib.widget.ActionButtonsPreference;
import com.android.settingslib.widget.FooterPreference;
import com.android.settingslib.widget.MainSwitchPreference;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.android.permissioncontroller.permission.ui.handheld.UtilsKt.pressBack;
import static com.android.permissioncontroller.sscopes.StorageScopesUtils.STORAGE_PERMISSION_TYPE_ALL_FILES_ACCESS;
import static com.android.permissioncontroller.sscopes.StorageScopesUtils.STORAGE_PERMISSION_TYPE_MEDIA_MANAGEMENT;
import static com.android.permissioncontroller.sscopes.StorageScopesUtils.arrayListOf;
import static com.android.permissioncontroller.sscopes.StorageScopesUtils.getFullLabelForPackage;
import static com.android.permissioncontroller.sscopes.StorageScopesUtils.getStorageScopes;
import static com.android.permissioncontroller.sscopes.StorageScopesUtils.packageHasStoragePermission;
import static com.android.permissioncontroller.sscopes.StorageScopesUtils.storageScopesEnabled;

public final class StorageScopesFragment extends SettingsWithLargeHeader {
    private static final String TAG = "StorageScopesFragment";

    private String pkgName;

    private Context context;
    private PreferenceScreen preferenceScreen;

    private MainSwitchPreference mainSwitch;
    private ActionButtonsPreference actionButtons;

    private PreferenceCategory categoryFolders;
    private PreferenceCategory categoryFiles;

    private FooterPreference footer;

    public static Bundle createArgs(String packageName) {
        Bundle b = new Bundle();
        b.putString(Intent.EXTRA_PACKAGE_NAME, packageName);
        return b;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getActivity().setTitle(R.string.sscopes);
        setHasOptionsMenu(true); // needed for the "back arrow" button even when there's no menu

        Context context = requireContext();
        this.context = context;

        String pkgName = Objects.requireNonNull(getArguments().getString(Intent.EXTRA_PACKAGE_NAME));
        this.pkgName = pkgName;

        UserHandle user = Process.myUserHandle();
        Application application = getActivity().getApplication();
        String label = KotlinUtils.INSTANCE.getPackageLabel(application, pkgName, user);

        if (TextUtils.isEmpty(label)) {
            pressBack(this);
            return;
        }

        Drawable icon = KotlinUtils.INSTANCE.getBadgedPackageIcon(application, pkgName, user);
        setHeader(icon, label, null, user, false);

        {
            MainSwitchPreference s = new MainSwitchPreference(context);
            s.setTitle(R.string.sscopes_enable);
            s.addOnSwitchChangeListener((v, isChecked) -> {
                if (isChecked && !ignoreMainSwitchChange) {
                    setEnabled(true);
                }
            });

            mainSwitch = s;
        }
        {
            ActionButtonsPreference b = new ActionButtonsPreference(context);

            b.setButton1Text(R.string.sscopes_btn_add_folder);
            b.setButton1Icon(R.drawable.ic_sscopes_add_folder);
            b.setButton1OnClickListener(v -> launchPicker(true));

            b.setButton2Text(R.string.sscopes_btn_add_file);
            b.setButton2Icon(R.drawable.ic_sscopes_add_file);
            b.setButton2OnClickListener(v -> launchPicker(false));

            // TODO add a button to use MediaStore#ACTION_PICK_IMAGES after AOSP T comes out
            actionButtons = b;
        }
        categoryFolders = newCategory(R.string.sscopes_folders);
        categoryFiles = newCategory(R.string.sscopes_files);
        {
            FooterPreference f = new FooterPreference(context);
            f.setSelectable(false);
            footer = f;
        }

        preferenceScreen = getPreferenceManager().createPreferenceScreen(context);
        setPreferenceScreen(preferenceScreen);
    }

    private boolean skipUpdates;

    void update() {
        if (skipUpdates) {
            return;
        }

        final int storagePermissionType = packageHasStoragePermission(context, pkgName);

        if (storagePermissionType != 0) {
            AlertDialog.Builder b = new AlertDialog.Builder(context);
            b.setMessage(R.string.sscopes_deny_storage_permissions);
            b.setOnDismissListener(dialog -> getActivity().finish());
            b.setPositiveButton(R.string.sscopes_open_settings, (dialog, which) -> {
                Uri uri = Uri.fromParts("package", pkgName, null);
                Intent i;
                switch (storagePermissionType) {
                    case STORAGE_PERMISSION_TYPE_ALL_FILES_ACCESS:
                        i = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri);
                        break;
                    case STORAGE_PERMISSION_TYPE_MEDIA_MANAGEMENT:
                        i = new Intent(Settings.ACTION_REQUEST_MANAGE_MEDIA, uri);
                        break;
                    default:
                        i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri);
                }
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            });

            b.show();

            preferenceScreen.removeAll();
            skipUpdates = true;
            return;
        }

        StorageScope[] scopes = getStorageScopes(pkgName);
        boolean enabled = scopes != null;

        addOrRemove(mainSwitch, !enabled);

        if (!enabled) {
            ignoreMainSwitchChange = true;
            mainSwitch.setChecked(false);
            ignoreMainSwitchChange = false;
        }

        addOrRemove(actionButtons, enabled);
        addOrRemove(categoryFolders, enabled);
        addOrRemove(categoryFiles, enabled);

        updateListOfScopes(scopes);

        PackageManager pm = context.getPackageManager();
        String[] uidPkgs = null;
        try {
            int uid = pm.getPackageUid(pkgName, 0);
            uidPkgs = pm.getPackagesForUid(uid);
        } catch (Exception e) {
            Log.d(TAG, "", e);
        }

        if (uidPkgs == null) {
            pressBack(this);
            return;
        }

        boolean addFooter = scopes == null || scopes.length == 0 || uidPkgs.length > 1;
        if (addFooter) {
            StringBuilder summary = new StringBuilder();
            if (scopes == null) {
                summary.append(getString(R.string.sscopes_disabled_footer));
            } else if (scopes.length == 0) {
                summary.append(getString(R.string.sscopes_empty_footer));
            }

            if (uidPkgs.length > 1) {
                if (summary.length() != 0) {
                    summary.append("\n\n");
                }
                summary.append(getString(R.string.sscopes_shared_uid_warning, getFullLabelForPackage(
                        getActivity().getApplication(), uidPkgs, Process.myUserHandle())));
            }

            footer.setSummary(summary.toString());

            if (scopes == null || scopes.length == 0) {
                footer.setLearnMoreAction((v) -> {
                    String link = "https://grapheneos.org/usage#storage-access";
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(link)));
                });
            }
        }
        addOrRemove(footer, addFooter);

        getActivity().invalidateOptionsMenu();
    }

    private void setEnabled(boolean enabled) {
        GosPackageState curPs = GosPackageState.get(pkgName);
        int curFlags = curPs != null ? curPs.flags : 0;

        int newPsFlags;
        boolean killUid;
        if (enabled) {
            newPsFlags = curFlags | GosPackageState.FLAG_STORAGE_SCOPES_ENABLED;
            // GMS often needs a restart to properly handle permission grants
            killUid = GmsCompat.isGmsApp(pkgName, Process.myUserHandle().getIdentifier());
        } else {
            newPsFlags = curFlags & (~GosPackageState.FLAG_STORAGE_SCOPES_ENABLED);
            killUid = true;
        }

        GosPackageState newPs = GosPackageState.set(pkgName, newPsFlags, null, killUid);

        invalidateMediaProviderCache(null);

        if (newPs == null && enabled) {
            // failed to updated GosPackageState, likely because package was uninstalled
            pressBack(this);
            return;
        }

        update();
    }

    boolean ignoreMainSwitchChange;

    private void updateListOfScopes(StorageScope[] scopes) {
        categoryFolders.removeAll();
        categoryFiles.removeAll();

        if (scopes == null) {
            categoryFolders.setVisible(false);
            categoryFiles.setVisible(false);
            return;
        }

        Context ctx = context;

        List<StorageVolume> storageVolumes = ctx.getSystemService(StorageManager.class).getStorageVolumes();

        for (StorageScope scope : scopes) {
            PreferenceCategory category;
            if (scope.isDirectory()) {
                category = categoryFolders;
            } else if (scope.isFile()) {
                category = categoryFiles;
            } else {
                throw new IllegalStateException();
            }

            StorageScopePreference p = new StorageScopePreference(ctx);
            p.setTitle(StorageScopesUtils.pathToUiLabel(ctx, storageVolumes, new File(scope.path)));

            p.setRightIcon(ctx.getDrawable(R.drawable.ic_sscopes_remove), v -> removeScope(scope));
            p.setSelectable(false);

            p.setKey(scope.path);

            category.addPreference(p);
        }

        categoryFolders.setVisible(categoryFolders.getPreferenceCount() != 0);
        categoryFiles.setVisible(categoryFiles.getPreferenceCount() != 0);
    }

    void removeScope(StorageScope scope) {
        GosPackageState ps = GosPackageState.get(pkgName);

        if (ps == null) {
            return;
        }

        StorageScope[] scopesArray = StorageScope.deserializeArray(ps);
        ArrayList<StorageScope> scopesList = arrayListOf(scopesArray);

        if (!scopesList.remove(scope)) {
            return;
        }

        scopesArray = scopesList.toArray(new StorageScope[0]);

        byte[] serializedScopes = StorageScope.serializeArray(scopesArray);
        int flags = ps.flags | GosPackageState.FLAG_STORAGE_SCOPES_ENABLED;

        GosPackageState newPs = null;
        try {
            newPs = GosPackageState.set(pkgName, flags, serializedScopes, false);
        } catch (IllegalArgumentException e) {
            Log.d(TAG, "unable to remove StorageScope", e);
        }

        if (newPs == null) {
            pressBack(this);
            return;
        }

        ArrayList<String> changedPaths = new ArrayList<>();
        changedPaths.add(scope.path);
        invalidateMediaProviderCache(changedPaths);

        update();
    }

    private PreferenceCategory newCategory(int title) {
        PreferenceCategory c = new PreferenceCategory(context);
        c.setTitle(title);
        c.setKey(Integer.toString(title));
        return c;
    }

    void launchPicker(boolean dir) {
        StorageScope[] scopes = getStorageScopes(pkgName);
        if (scopes == null) {
            pressBack(this);
            return;
        }
        if (scopes.length >= StorageScope.maxArrayLength()) {
            showToast(R.string.sscopes_list_is_full);
            return;
        }

        Intent i = new Intent(dir ? Intent.ACTION_OPEN_DOCUMENT_TREE : Intent.ACTION_OPEN_DOCUMENT);
        i.putExtra(Intent.EXTRA_PACKAGE_NAME, pkgName);

        ArrayList<String> allowedAuthorities = new ArrayList<>();
        allowedAuthorities.add(DocumentsContract.EXTERNAL_STORAGE_PROVIDER_AUTHORITY);

        if (!dir) {
            i.setType("*/*");
            i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            i.addCategory(Intent.CATEGORY_OPENABLE);

            // com.android.providers.media.MediaDocumentsProvider
            allowedAuthorities.add("com.android.providers.media.documents");
            // com.android.providers.downloads.DownloadStorageProvider
            allowedAuthorities.add("com.android.providers.downloads.documents");
        }

        i.putStringArrayListExtra(Intent.EXTRA_RESTRICTIONS_LIST, allowedAuthorities);

        //noinspection deprecation
        startActivityForResult(i, dir ? REQUEST_CODE_DIR : REQUEST_CODE_FILE);
    }

    private static final int REQUEST_CODE_DIR = 1;
    private static final int REQUEST_CODE_FILE = 2;

    /** @noinspection deprecation*/
    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent intent) {

        if (requestCode != REQUEST_CODE_DIR && requestCode != REQUEST_CODE_FILE) {
            return;
        }

        if (resultCode != Activity.RESULT_OK || intent == null) {
            return;
        }

        GosPackageState curPkgState = GosPackageState.get(pkgName);
        if (!storageScopesEnabled(curPkgState)) {
            // other instance of this fragment updated curPkgState
            pressBack(this);
            return;
        }

        int scopeFlags = 0;

        if (curPkgState.hasDerivedFlag(GosPackageState.DFLAG_EXPECTS_STORAGE_WRITE_ACCESS)) {
            scopeFlags |= StorageScope.FLAG_ALLOW_WRITES;
        }

        ArrayList<StorageScope> newScopes = new ArrayList<>();

        if (requestCode == REQUEST_CODE_DIR) {
            Uri uri = Objects.requireNonNull(intent.getData());

            String path = StorageScopesUtils.dirUriToPath(context, uri);

            if (path != null) {
                scopeFlags |= StorageScope.FLAG_IS_DIR;
                newScopes.add(new StorageScope(path, scopeFlags));
            }
        } else {
            ClipData clipData = intent.getClipData();

            if (clipData != null) {
                List<StorageVolume> cachedVolumes = context.getSystemService(StorageManager.class).getStorageVolumes();

                for (int i = 0, m = clipData.getItemCount(); i < m; ++i) {
                    ClipData.Item item = clipData.getItemAt(i);
                    Uri uri = Objects.requireNonNull(item.getUri());

                    String path = StorageScopesUtils.fileUriToPath(context, uri, cachedVolumes);
                    if (path != null) {
                        newScopes.add(new StorageScope(path, scopeFlags));
                    }
                }
            } else {
                Uri uri = Objects.requireNonNull(intent.getData());

                String path = StorageScopesUtils.fileUriToPath(context, uri, null);
                if (path != null) {
                    newScopes.add(new StorageScope(path, scopeFlags));
                }
            }
        }

        if (newScopes.size() == 0) {
            showToast(R.string.sscopes_unknown_location);
            return;
        }

        ArrayList<StorageScope> currentScopes = arrayListOf(StorageScope.deserializeArray(curPkgState));

        if (currentScopes.size() >= StorageScope.maxArrayLength()) {
            showToast(R.string.sscopes_list_is_full);
            return;
        }

        ArrayList<String> addedPaths = new ArrayList<>();

        for (StorageScope newScope : newScopes) {
            if (currentScopes.contains(newScope)) {
                continue;
            }

            if (currentScopes.size() >= StorageScope.maxArrayLength()) {
                break;
            }
            currentScopes.add(0, newScope);
            addedPaths.add(newScope.path);
        }

        if (addedPaths.size() == 0) {
            return;
        }

        StorageScope[] scopesArray = currentScopes.toArray(new StorageScope[0]);

        GosPackageState newPkgState = GosPackageState.set(pkgName, curPkgState.flags | GosPackageState.FLAG_STORAGE_SCOPES_ENABLED,
                StorageScope.serializeArray(scopesArray), false);

        invalidateMediaProviderCache(addedPaths);

        if (newPkgState == null) {
            // unable to save newPkgState, likely because package was racily uninstalled
            pressBack(this);
            return;
        }
    }

    private void invalidateMediaProviderCache(@Nullable ArrayList<String> changedPaths) {
        Bundle args = new Bundle(2);
        args.putString(Intent.EXTRA_PACKAGE_NAME, pkgName);
        args.putStringArrayList(Intent.EXTRA_TEXT, changedPaths);

        ContentResolver cr = context.getContentResolver();

        try {
            cr.call(MediaStore.AUTHORITY, StorageScope.MEDIA_PROVIDER_METHOD_INVALIDATE_MEDIA_PROVIDER_CACHE,
                    null, args);
        } catch (Exception e) {
            Log.d(TAG, "", e);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        if (storageScopesEnabled(pkgName)) {
            menuItemTurnOff = menu.add(R.string.sscopes_menu_item_turn_off);
        }
    }

    private MenuItem menuItemTurnOff;

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            pressBack(this);
            return true;
        }

        if (menuItemTurnOff == item) {
            setEnabled(false);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    private void addOrRemove(Preference p, boolean add) {
        addOrRemove(p, preferenceScreen, add);
    }

    private static void addOrRemove(Preference p, PreferenceGroup group, boolean add) {
        boolean added = p.getParent() != null;
        if (add == added) {
            return;
        }
        if (add) {
            group.addPreference(p);
        } else {
            group.removePreference(p);
        }
    }

    private Toast toast;

    private void showToast(int text) {
        showToast(getString(text), Toast.LENGTH_SHORT);
    }

    private void showToast(String text, int duration) {
        if (toast != null) {
            toast.cancel();
        }

        toast = Toast.makeText(context, text, duration);
        toast.show();
    }

    @Override
    public void onResume() {
        super.onResume();
        update();
    }
}
