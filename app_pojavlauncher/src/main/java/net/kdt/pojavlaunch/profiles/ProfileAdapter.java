package net.kdt.pojavlaunch.profiles;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fr.spse.extended_view.ExtendedTextView;

/*
 * Adapter for listing launcher profiles in a Spinner
 */
public class ProfileAdapter extends BaseAdapter {
    private Map<String, MinecraftProfile> mProfiles;
    private final MinecraftProfile dummy = new MinecraftProfile();
    private List<String> mProfileList;
    private ProfileAdapterExtra[] mExtraEntires;

    public ProfileAdapter(ProfileAdapterExtra[] extraEntries) {
        reloadProfiles(extraEntries);
    }
    /*
     * Gets how much profiles are loaded in the adapter right now
     * @returns loaded profile count
     */
    @Override
    public int getCount() {
        return mProfileList.size() + mExtraEntires.length;
    }
    /*
     * Gets the profile at a given index
     * @param position index to retreive
     * @returns MinecraftProfile name or null
     */
    @Override
    public Object getItem(int position) {
        int profileListSize = mProfileList.size();
        int extraPosition = position - profileListSize;
        if(position < profileListSize){
            String profileName = mProfileList.get(position);
            if(mProfiles.containsKey(profileName)) return profileName;
        }else if(extraPosition >= 0 && extraPosition < mExtraEntires.length) {
            return mExtraEntires[extraPosition];
        }
        return null;
    }



    public int resolveProfileIndex(String name) {
        return mProfileList.indexOf(name);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public void notifyDataSetChanged() {
        mProfiles = new HashMap<>(LauncherProfiles.mainProfileJson.profiles);
        mProfileList = new ArrayList<>(Arrays.asList(mProfiles.keySet().toArray(new String[0])));
        sortProfileList();
        super.notifyDataSetChanged();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        if (v == null) v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_version_profile_layout,parent,false);
        Object item = getItem(position);
        if(item instanceof String) setViewProfile(v, (String) item, true, position);
        else setView(v, item, true);
        return v;
    }

    private void sortProfileList() {
        Collections.sort(mProfileList, (left, right) -> {
            MinecraftProfile leftProfile = mProfiles.get(left);
            MinecraftProfile rightProfile = mProfiles.get(right);
            String leftCategory = resolveVersionType(leftProfile == null ? null : leftProfile.lastVersionId);
            String rightCategory = resolveVersionType(rightProfile == null ? null : rightProfile.lastVersionId);
            int categoryCompare = leftCategory.compareTo(rightCategory);
            if(categoryCompare != 0) return categoryCompare;
            return left.compareToIgnoreCase(right);
        });
    }

    private String resolveVersionType(String versionName) {
        if(versionName == null) return "Vanilla";
        String lowerVersion = versionName.toLowerCase();
        if(lowerVersion.contains("forge")) return "Forge";
        if(lowerVersion.contains("fabric")) return "Fabric";
        return "Vanilla";
    }

    public void setViewProfile(View v, String nm, boolean displaySelection) {
        setViewProfile(v, nm, displaySelection, -1);
    }

    public void setViewProfile(View v, String nm, boolean displaySelection, int position) {
        MinecraftProfile minecraftProfile = mProfiles.get(nm);
        if(minecraftProfile == null) minecraftProfile = dummy;
        Drawable cachedIcon = ProfileIconCache.fetchIcon(v.getResources(), nm, minecraftProfile.icon);

        // Historically, the profile name "New" was hardcoded as the default profile name
        // We consider "New" the same as putting no name at all
        String profileName = (Tools.isValidString(minecraftProfile.name) && !"New".equalsIgnoreCase(minecraftProfile.name)) ? minecraftProfile.name : null;
        String versionName = minecraftProfile.lastVersionId;

        if (MinecraftProfile.LATEST_RELEASE.equalsIgnoreCase(versionName))
            versionName = v.getContext().getString(R.string.profiles_latest_release);
        else if (MinecraftProfile.LATEST_SNAPSHOT.equalsIgnoreCase(versionName))
            versionName = v.getContext().getString(R.string.profiles_latest_snapshot);

        String displayName;
        if (versionName == null && profileName != null)
            displayName = profileName;
        else if (versionName != null && profileName == null)
            displayName = versionName;
        else displayName = String.format("%s - %s", profileName, versionName);

        if(v instanceof ExtendedTextView) {
            ExtendedTextView extendedTextView = (ExtendedTextView) v;
            extendedTextView.setCompoundDrawablesRelative(cachedIcon, null, extendedTextView.getCompoundsDrawables()[2], null);
            extendedTextView.setText(displayName);
            if(displaySelection){
                String selectedProfile = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE,"");
                extendedTextView.setBackgroundColor(selectedProfile.equals(nm) ? ColorUtils.setAlphaComponent(Color.WHITE,60) : Color.TRANSPARENT);
            }else extendedTextView.setBackgroundColor(Color.TRANSPARENT);
            return;
        }

        TextView categoryView = v.findViewById(R.id.version_category);
        TextView versionNumberView = v.findViewById(R.id.version_number);
        TextView versionTypeView = v.findViewById(R.id.version_type);
        ImageView iconView = v.findViewById(R.id.version_icon);
        View rowView = v.findViewById(R.id.version_row);

        String versionType = resolveVersionType(minecraftProfile.lastVersionId);
        boolean showHeader = position <= 0;
        if(position > 0) {
            Object previousItem = getItem(position - 1);
            if(previousItem instanceof String) {
                MinecraftProfile previousProfile = mProfiles.get(previousItem);
                showHeader = !versionType.equals(resolveVersionType(previousProfile == null ? null : previousProfile.lastVersionId));
            }
        }
        categoryView.setVisibility(showHeader ? View.VISIBLE : View.GONE);
        categoryView.setText(versionType);
        versionNumberView.setText(displayName);
        versionTypeView.setText(versionType);
        iconView.setImageDrawable(cachedIcon);

        String selectedProfile = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE,"");
        rowView.setAlpha(!displaySelection || selectedProfile.equals(nm) ? 1f : 0.82f);
    }

    public void setViewExtra(View v, ProfileAdapterExtra extra) {
        if(v instanceof ExtendedTextView) {
            ExtendedTextView extendedTextView = (ExtendedTextView) v;
            extendedTextView.setCompoundDrawablesRelative(extra.icon, null, extendedTextView.getCompoundsDrawables()[2], null);
            extendedTextView.setText(extra.name);
            extendedTextView.setBackgroundColor(Color.TRANSPARENT);
            return;
        }
        TextView categoryView = v.findViewById(R.id.version_category);
        TextView versionNumberView = v.findViewById(R.id.version_number);
        TextView versionTypeView = v.findViewById(R.id.version_type);
        ImageView iconView = v.findViewById(R.id.version_icon);
        ImageView downloadIcon = v.findViewById(R.id.version_download_icon);
        categoryView.setVisibility(View.VISIBLE);
        categoryView.setText("Create");
        versionNumberView.setText(extra.name);
        versionTypeView.setText("Profile");
        iconView.setImageDrawable(extra.icon);
        downloadIcon.setImageResource(R.drawable.ic_add);
    }

    public void setView(View v, Object object, boolean displaySelection) {
        if(object instanceof String) {
            setViewProfile(v, (String) object, displaySelection);
        }else if(object instanceof ProfileAdapterExtra) {
            setViewExtra(v, (ProfileAdapterExtra) object);
        }
    }

    /** Reload profiles from the file */
    public void reloadProfiles(){
        LauncherProfiles.load();
        mProfiles = new HashMap<>(LauncherProfiles.mainProfileJson.profiles);
        mProfileList = new ArrayList<>(Arrays.asList(mProfiles.keySet().toArray(new String[0])));
        sortProfileList();
        notifyDataSetChanged();
    }

    /** Reload profiles from the file, with additional extra entries */
    public void reloadProfiles(ProfileAdapterExtra[] extraEntries) {
        if(extraEntries == null) mExtraEntires = new ProfileAdapterExtra[0];
        else mExtraEntires = extraEntries;
        this.reloadProfiles();
    }
}
