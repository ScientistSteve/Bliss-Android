package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.mcVersionSpinner;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.servers.ServerDataManager;
import net.kdt.pojavlaunch.servers.ServerModels;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainMenuFragment extends Fragment {
    private mcVersionSpinner mVersionSpinner;
    private boolean isQuickActionsListMode = false;
    private boolean isServersCompactMode = false;
    private final List<ServerModels.ServerEntry> servers = new ArrayList<>();
    private LinearLayout serversContainer;
    private final ExecutorService serverExecutor = Executors.newCachedThreadPool();
    public MainMenuFragment(){ super(R.layout.fragment_launcher); }
    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        View mCustomControlButton = view.findViewById(R.id.custom_control_button);
        View mInstallJarButton = view.findViewById(R.id.install_jar_button);
        View mShareLogsButton = view.findViewById(R.id.share_logs_button);
        View mOpenDirectoryButton = view.findViewById(R.id.open_files_button);
        ImageButton mEditProfileButton = view.findViewById(R.id.edit_profile_button);
        View mPlayButton = view.findViewById(R.id.play_button);
        LinearLayout quickActionsContainer = view.findViewById(R.id.quick_actions_container);
        ImageButton quickActionsToggleButton = view.findViewById(R.id.quick_actions_toggle_button);
        ImageButton serversAddButton = view.findViewById(R.id.servers_add_button);
        ImageButton serversToggleButton = view.findViewById(R.id.servers_toggle_button);
        serversContainer = view.findViewById(R.id.servers_container);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);
        if (quickActionsContainer != null) updateQuickActionsLayout(quickActionsContainer);
        if (quickActionsToggleButton != null && quickActionsContainer != null) quickActionsToggleButton.setOnClickListener(v -> { isQuickActionsListMode=!isQuickActionsListMode; updateQuickActionsLayout(quickActionsContainer);} );
        if (serversToggleButton != null) serversToggleButton.setOnClickListener(v -> { isServersCompactMode=!isServersCompactMode; renderServers();});
        if (serversAddButton != null) serversAddButton.setOnClickListener(v -> openServerEditor(null));
        mCustomControlButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        mInstallJarButton.setOnClickListener(v -> runInstallerWithConfirmation(false));
        mInstallJarButton.setOnLongClickListener(v -> { runInstallerWithConfirmation(true); return true; });
        mEditProfileButton.setOnClickListener(v -> mVersionSpinner.openProfileEditor(requireActivity()));
        mPlayButton.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));
        mShareLogsButton.setOnClickListener((v) -> shareLog(requireContext()));
        mOpenDirectoryButton.setOnClickListener((v)-> openPath(v.getContext(), getCurrentProfileDirectory(), false));
        loadServers();
    }
    private void loadServers(){ servers.clear(); servers.addAll(ServerDataManager.readServers(getCurrentProfileDirectory())); renderServers(); for (ServerModels.ServerEntry s:servers) pingAndRender(s); }
    private void renderServers(){ if(serversContainer==null) return; serversContainer.removeAllViews(); LayoutInflater inflater=LayoutInflater.from(requireContext()); for(ServerModels.ServerEntry s:servers){ View row=inflater.inflate(R.layout.item_server_entry, serversContainer, false); ImageView icon=row.findViewById(R.id.server_icon); TextView name=row.findViewById(R.id.server_name); TextView status=row.findViewById(R.id.server_status); TextView edit=row.findViewById(R.id.server_edit); name.setText(s.name); status.setText(getStatusText(s)); setIcon(icon,s.iconBase64); edit.setOnClickListener(v->openServerEditor(s)); View text=row.findViewById(R.id.server_text_holder); edit.setVisibility(isServersCompactMode?View.GONE:View.VISIBLE); text.setVisibility(isServersCompactMode?View.GONE:View.VISIBLE); serversContainer.addView(row);} }
    private String getStatusText(ServerModels.ServerEntry s){ if(s.state==ServerModels.PingState.CONNECTING) return "🟡 Connecting..."; if(s.state==ServerModels.PingState.OFFLINE) return "🔴 Offline"; return "🟢 "+s.onlinePlayers+"/"+s.maxPlayers+" Players · "+s.pingMs+"pms"; }
    private void setIcon(ImageView imageView, String favicon){ if(Tools.isValidString(favicon)&&favicon.startsWith("data:image")){ try{ byte[] bytes= Base64.decode(favicon.substring(favicon.indexOf(',')+1), Base64.DEFAULT); Bitmap bm= BitmapFactory.decodeByteArray(bytes,0,bytes.length); if(bm!=null){ imageView.setImageBitmap(bm); return; }}catch(Throwable ignored){} } imageView.setImageResource(R.drawable.ic_menu_play); }
    private void pingAndRender(ServerModels.ServerEntry s){ serverExecutor.execute(()->{ ServerDataManager.pingServer(s); if(isAdded()) requireActivity().runOnUiThread(this::renderServers);}); }
    private void openServerEditor(@Nullable ServerModels.ServerEntry entry){ View v=LayoutInflater.from(requireContext()).inflate(android.R.layout.simple_list_item_2,null,false); EditText name=new EditText(requireContext()); name.setHint("Server name"); EditText ip=new EditText(requireContext()); ip.setHint("Server IP"); LinearLayout ll=new LinearLayout(requireContext()); ll.setOrientation(LinearLayout.VERTICAL); ll.addView(name); ll.addView(ip); if(entry!=null){name.setText(entry.name); ip.setText(entry.ip);} new AlertDialog.Builder(requireContext()).setTitle(entry==null?"Add Server":"Edit Server").setView(ll).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{ String n=name.getText().toString().trim(); String i=ip.getText().toString().trim(); if(!Tools.isValidString(n)||!Tools.isValidString(i)) return; ServerModels.ServerEntry target=entry; if(target==null){ target=new ServerModels.ServerEntry(n,i,null); servers.add(target);} else {target.name=n; target.ip=i;} renderServers(); pingAndRender(target); ServerDataManager.writeServers(getCurrentProfileDirectory(),servers); }).show(); }
    private void updateQuickActionsLayout(LinearLayout c) { c.setOrientation(isQuickActionsListMode ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL); }
    private File getCurrentProfileDirectory() { String cp=LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null); if(!Tools.isValidString(cp)) return new File(Tools.DIR_GAME_NEW); LauncherProfiles.load(); MinecraftProfile p=LauncherProfiles.mainProfileJson.profiles.get(cp); if(p==null) return new File(Tools.DIR_GAME_NEW); return Tools.getGameDirPath(p); }
    @Override public void onResume() { super.onResume(); mVersionSpinner.reloadProfiles(); }
    private void runInstallerWithConfirmation(boolean isCustomArgs) { if (ProgressKeeper.getTaskCount() == 0) Tools.installMod(requireActivity(), isCustomArgs); else Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show(); }
}
