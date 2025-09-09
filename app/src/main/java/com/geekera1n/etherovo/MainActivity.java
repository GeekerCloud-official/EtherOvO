package com.geekera1n.etherovo; // 确保包名一致

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.LinkAddress;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.LinkProperties;
import android.net.RouteInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private LinearLayout layoutDisconnected;
    private ScrollView layoutConnected;
    private MaterialToolbar topAppBar;
    private TextView tvInterfaceName;
    private Button btnAddIp, btnAddRoute, btnResetInterface;
    private ListView lvInterfaceDetails, lvIpAddresses, lvRoutes;
    private TextView tvDisconnectedStatus;

    private InfoAdapter detailsAdapter, ipAdapter, routeAdapter;
    private final List<InfoItem> detailsList = new ArrayList<>();
    private final List<InfoItem> ipList = new ArrayList<>();
    private final List<InfoItem> routeList = new ArrayList<>();

    private String currentInterfaceName = "";
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Handler autoRefreshHandler;
    private Runnable autoRefreshRunnable;
    private boolean isAutoRefreshing = false;

    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "EtherOvO_Persistence";
    private static final String KEY_PERSISTENT_IPS = "persistent_ips";
    private static final String KEY_PERSISTENT_ROUTES = "persistent_routes";
    private boolean wasInterfaceUp = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        initViews();
        setupListeners();
        refreshAllInfo(true);
    }

    @Override
    protected void onResume() { super.onResume(); startAutoRefresh(); }
    @Override
    protected void onPause() { super.onPause(); stopAutoRefresh(); }

    private void initViews() {
        layoutDisconnected = findViewById(R.id.layoutDisconnected);
        layoutConnected = findViewById(R.id.layoutConnected);
        topAppBar = findViewById(R.id.topAppBar);
        tvInterfaceName = findViewById(R.id.tvInterfaceName);
        lvInterfaceDetails = findViewById(R.id.lvInterfaceDetails);
        btnAddIp = findViewById(R.id.btnAddIp);
        btnAddRoute = findViewById(R.id.btnAddRoute);
        btnResetInterface = findViewById(R.id.btnResetInterface);
        lvIpAddresses = findViewById(R.id.lvIpAddresses);
        lvRoutes = findViewById(R.id.lvRoutes);
        tvDisconnectedStatus = findViewById(R.id.tv_disconnected_status);

        detailsAdapter = new InfoAdapter(this, detailsList);
        ipAdapter = new InfoAdapter(this, ipList);
        routeAdapter = new InfoAdapter(this, routeList);

        lvInterfaceDetails.setAdapter(detailsAdapter);
        lvIpAddresses.setAdapter(ipAdapter);
        lvRoutes.setAdapter(routeAdapter);
    }

    private void setupListeners() {
        topAppBar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_refresh) {
                refreshAllInfo(true);
                return true;
            }
            if (item.getItemId() == R.id.action_info) {
                showAboutDialog();
                return true;
            }
            return false;
        });

        if (RootUtil.isRootAvailable()) {
            btnAddIp.setOnClickListener(v -> showAddIpDialog());
            btnAddRoute.setOnClickListener(v -> showAddRouteDialog());
            btnResetInterface.setOnClickListener(v -> resetInterface());
            lvIpAddresses.setOnItemLongClickListener((parent, view, position, id) -> {
                String fullIp = ipList.get(position).value;
                showDeleteConfirmationDialog(getString(R.string.item_type_ip), fullIp, () -> deleteIpAddress(fullIp));
                return true;
            });
            lvRoutes.setOnItemLongClickListener((parent, view, position, id) -> {
                String fullRoute = routeList.get(position).originalValue != null ? routeList.get(position).originalValue : routeList.get(position).value;
                showDeleteConfirmationDialog(getString(R.string.item_type_route), fullRoute, () -> deleteRoute(fullRoute));
                return true;
            });
        }
    }

    private void updateUiState(boolean isConnected) {
        if (isConnected) {
            layoutConnected.setVisibility(View.VISIBLE);
            layoutDisconnected.setVisibility(View.GONE);
            findViewById(R.id.ip_section_title).setVisibility(View.VISIBLE);
            findViewById(R.id.route_section_title).setVisibility(View.VISIBLE);
            lvIpAddresses.setVisibility(View.VISIBLE);
            lvRoutes.setVisibility(View.VISIBLE);
            boolean hasRoot = RootUtil.isRootAvailable();
            int visibility = hasRoot ? View.VISIBLE : View.GONE;
            btnAddIp.setVisibility(visibility);
            btnAddRoute.setVisibility(visibility);
            btnResetInterface.setVisibility(visibility);
            if (!hasRoot) {
                lvIpAddresses.setOnItemLongClickListener(null);
                lvRoutes.setOnItemLongClickListener(null);
            }
        } else {
            layoutConnected.setVisibility(View.GONE);
            layoutDisconnected.setVisibility(View.VISIBLE);
        }
    }

    private void refreshAllInfo(boolean showToast) {
        if (showToast) {
            Toast.makeText(this, getString(R.string.refreshing), Toast.LENGTH_SHORT).show();
        }
        if (RootUtil.isRootAvailable()) {
            refreshInfoWithRoot(showToast);
        } else {
            refreshInfoWithAndroidApi(showToast);
        }
    }

    private void refreshInfoWithAndroidApi(boolean showToast) {
        executorService.execute(() -> {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network activeNetwork = null;
            for (Network network : cm.getAllNetworks()) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(network);
                if (caps != null && caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) {
                    activeNetwork = network;
                    break;
                }
            }

            if (activeNetwork == null) {
                mainHandler.post(() -> {
                    if (tvDisconnectedStatus != null) tvDisconnectedStatus.setText(getString(R.string.no_wired_connection));
                    updateUiState(false);
                    if (showToast) Toast.makeText(this, getString(R.string.ethernet_not_found), Toast.LENGTH_SHORT).show();
                });
                return;
            }

            LinkProperties linkProperties = cm.getLinkProperties(activeNetwork);
            if (linkProperties == null) {
                mainHandler.post(() -> updateUiState(false));
                return;
            }

            final String interfaceName = linkProperties.getInterfaceName();
            final List<InfoItem> newDetailsList = new ArrayList<>();
            final List<InfoItem> newIpList = new ArrayList<>();
            final List<InfoItem> newRouteList = new ArrayList<>();

            for (LinkAddress addr : linkProperties.getLinkAddresses()) {
                if (addr.getAddress() instanceof Inet4Address) {
                    newIpList.add(new InfoItem(getString(R.string.ipv4_address), addr.toString()));
                }
            }

            linkProperties.getRoutes().forEach(route -> {
                if (route.hasGateway()) {
                    String title = route.isDefaultRoute() ? getString(R.string.default_route_gateway) : getString(R.string.route_to, route.getDestination().toString());
                    newRouteList.add(new InfoItem(title, route.toString()));
                }
            });

            StringBuilder dnsBuilder = new StringBuilder();
            for (InetAddress dns : linkProperties.getDnsServers()) {
                dnsBuilder.append(dns.getHostAddress()).append("\n");
            }

            String macAddress = "N/A";
            try {
                List<NetworkInterface> all = Collections.list(NetworkInterface.getNetworkInterfaces());
                for (NetworkInterface nif : all) {
                    if (!nif.getName().equalsIgnoreCase(interfaceName)) continue;
                    byte[] macBytes = nif.getHardwareAddress();
                    if (macBytes == null) continue;
                    StringBuilder res1 = new StringBuilder();
                    for (byte b : macBytes) res1.append(String.format("%02X:", b));
                    if (res1.length() > 0) res1.deleteCharAt(res1.length() - 1);
                    macAddress = res1.toString();
                    break;
                }
            } catch (Exception ex) { ex.printStackTrace(); }

            newDetailsList.add(new InfoItem(getString(R.string.mac_address), macAddress));
            newDetailsList.add(new InfoItem(getString(R.string.status), "UP"));
            if (dnsBuilder.length() > 0) newDetailsList.add(new InfoItem(getString(R.string.dns_servers), dnsBuilder.toString().trim()));
            newDetailsList.add(new InfoItem(getString(R.string.speed), getString(R.string.na_non_root)));
            newDetailsList.add(new InfoItem(getString(R.string.duplex_mode), getString(R.string.na_non_root)));

            mainHandler.post(() -> {
                updateUiState(true);
                tvInterfaceName.setText(interfaceName);
                updateListView(detailsList, newDetailsList, detailsAdapter, lvInterfaceDetails);
                updateListView(ipList, newIpList, ipAdapter, lvIpAddresses);
                updateListView(routeList, newRouteList, routeAdapter, lvRoutes);
                if (showToast) Toast.makeText(this, getString(R.string.refresh_complete), Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void refreshInfoWithRoot(boolean showToast) {
        executorService.execute(() -> {
            RootUtil.CommandResult resultLinks = RootUtil.executeRootCommand("ip link show");
            Pattern interfacePattern = Pattern.compile("\\d+: (eth\\d+|usb\\d+):");
            Matcher interfaceMatcher = interfacePattern.matcher(resultLinks.stdout);
            String foundInterfaceName = interfaceMatcher.find() ? interfaceMatcher.group(1) : "";

            if (foundInterfaceName.isEmpty()) {
                mainHandler.post(() -> {
                    if (tvDisconnectedStatus != null) tvDisconnectedStatus.setText(getString(R.string.usb_not_found));
                    this.currentInterfaceName = "";
                    this.wasInterfaceUp = false;
                    updateUiState(false);
                    if (showToast) Toast.makeText(this, getString(R.string.usb_not_found), Toast.LENGTH_SHORT).show();
                });
                return;
            }

            final boolean isNewInterfaceConnected = !foundInterfaceName.equals(this.currentInterfaceName);
            RootUtil.CommandResult resultLink = RootUtil.executeRootCommand("ip link show " + foundInterfaceName);
            String linkOutput = resultLink.stdout;
            boolean isUp = linkOutput.contains("state UP");

            final boolean shouldRestoreConfig = isNewInterfaceConnected || (!this.wasInterfaceUp && isUp);
            if (shouldRestoreConfig) {
                applyPersistentConfig(foundInterfaceName);
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                resultLink = RootUtil.executeRootCommand("ip link show " + foundInterfaceName);
                linkOutput = resultLink.stdout;
                isUp = linkOutput.contains("state UP");
            }

            StringBuilder dnsBuilder = new StringBuilder();
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            for (Network network : cm.getAllNetworks()) {
                LinkProperties linkProperties = cm.getLinkProperties(network);
                if (linkProperties != null && foundInterfaceName.equals(linkProperties.getInterfaceName())) {
                    for (InetAddress dns : linkProperties.getDnsServers()) {
                        dnsBuilder.append(dns.getHostAddress()).append("\n");
                    }
                    break;
                }
            }

            String macAddress = "N/A";
            Pattern macPattern = Pattern.compile("link/ether ([0-9a-fA-F:]+)");
            Matcher macMatcher = macPattern.matcher(linkOutput);
            if (macMatcher.find()) macAddress = macMatcher.group(1);

            RootUtil.CommandResult resultAddr = RootUtil.executeRootCommand("ip addr show " + foundInterfaceName);
            final List<InfoItem> newIpList = new ArrayList<>();
            Pattern ipPattern = Pattern.compile("inet (\\d+\\.\\d+\\.\\d+\\.\\d+/\\d+)");
            Matcher ipMatcher = ipPattern.matcher(resultAddr.stdout);
            while (ipMatcher.find()) {
                newIpList.add(new InfoItem(getString(R.string.ipv4_address), ipMatcher.group(1)));
            }

            RootUtil.CommandResult speedResult = RootUtil.executeRootCommand("cat /sys/class/net/" + foundInterfaceName + "/speed");
            String speed = "N/A";
            if (isUp && speedResult.isSuccess() && !speedResult.stdout.trim().isEmpty()) {
                try {
                    String speedValue = speedResult.stdout.trim();
                    if (Integer.parseInt(speedValue) > 0) speed = speedValue + " Mbps";
                } catch (NumberFormatException e) { /* Ignore */ }
            }

            RootUtil.CommandResult duplexResult = RootUtil.executeRootCommand("cat /sys/class/net/" + foundInterfaceName + "/duplex");
            String duplex = "N/A";
            if (isUp && duplexResult.isSuccess() && !duplexResult.stdout.trim().isEmpty()) {
                String d = duplexResult.stdout.trim();
                duplex = d.substring(0, 1).toUpperCase() + d.substring(1);
            }

            final List<InfoItem> newDetailsList = new ArrayList<>();
            newDetailsList.add(new InfoItem(getString(R.string.mac_address), macAddress));
            newDetailsList.add(new InfoItem(getString(R.string.status), isUp ? "UP" : "DOWN"));
            if (dnsBuilder.length() > 0) {
                newDetailsList.add(new InfoItem(getString(R.string.dns_servers), dnsBuilder.toString().trim()));
            }
            newDetailsList.add(new InfoItem(getString(R.string.speed), speed));
            newDetailsList.add(new InfoItem(getString(R.string.duplex_mode), duplex));

            // +++ 修改：只使用 `ip route show table [接口名]` 来检测路由 +++
            final List<InfoItem> newRouteList = new ArrayList<>();
            RootUtil.CommandResult resultRoute = RootUtil.executeRootCommand("ip route show table " + foundInterfaceName);
            String[] lines = resultRoute.stdout.split("\\r?\\n");
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                String cleanedLine = line.trim();
                String[] parts = cleanedLine.split("\\s+");
                String destination = (parts.length > 0) ? parts[0] : "";
                String title;
                if (cleanedLine.startsWith("default")) {
                    title = getString(R.string.default_route_gateway);
                } else {
                    title = getString(R.string.route_to, destination);
                }
                InfoItem routeItem = new InfoItem(title, cleanedLine);
                routeItem.originalValue = cleanedLine;
                newRouteList.add(routeItem);
            }

            final String finalInterfaceName = foundInterfaceName;
            final boolean finalIsUp = isUp;

            mainHandler.post(() -> {
                this.currentInterfaceName = finalInterfaceName;
                this.wasInterfaceUp = finalIsUp;
                updateUiState(true);
                tvInterfaceName.setText(finalInterfaceName);
                updateListView(detailsList, newDetailsList, detailsAdapter, lvInterfaceDetails);
                updateListView(ipList, newIpList, ipAdapter, lvIpAddresses);
                updateListView(routeList, newRouteList, routeAdapter, lvRoutes);
                if (showToast) Toast.makeText(this, getString(R.string.refresh_complete), Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void updateListView(List<InfoItem> currentList, List<InfoItem> newList, InfoAdapter adapter, ListView listView) {
        if (!currentList.equals(newList)) {
            currentList.clear();
            currentList.addAll(newList);
            adapter.notifyDataSetChanged();
            listView.post(() -> setListViewHeightBasedOnChildren(listView));
        }
    }

    public static void setListViewHeightBasedOnChildren(ListView listView) {
        ListAdapter listAdapter = listView.getAdapter();
        if (listAdapter == null) return;
        int totalHeight = 0;
        if (listAdapter.getCount() > 0) {
            int desiredWidth = View.MeasureSpec.makeMeasureSpec(listView.getWidth(), View.MeasureSpec.EXACTLY);
            for (int i = 0; i < listAdapter.getCount(); i++) {
                View listItem = listAdapter.getView(i, null, listView);
                if (listView.getWidth() > 0) {
                    listItem.measure(desiredWidth, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                    totalHeight += listItem.getMeasuredHeight();
                }
            }
        }
        ViewGroup.LayoutParams params = listView.getLayoutParams();
        params.height = totalHeight + (listView.getDividerHeight() * (listAdapter.getCount() - 1));
        listView.setLayoutParams(params);
        listView.requestLayout();
    }

    private void startAutoRefresh() {
        if (isAutoRefreshing) return;
        isAutoRefreshing = true;
        autoRefreshHandler = new Handler(Looper.getMainLooper());
        autoRefreshRunnable = new Runnable() {
            @Override
            public void run() { refreshAllInfo(false); autoRefreshHandler.postDelayed(this, 2000); }
        };
        autoRefreshHandler.post(autoRefreshRunnable);
    }

    private void stopAutoRefresh() {
        if (autoRefreshHandler != null && autoRefreshRunnable != null) {
            autoRefreshHandler.removeCallbacks(autoRefreshRunnable);
        }
        isAutoRefreshing = false;
    }

    private void executeCommandAndRefresh(String command, String successMessage) {
        stopAutoRefresh();
        Toast.makeText(this, getString(R.string.executing_action), Toast.LENGTH_SHORT).show();
        executorService.execute(() -> {
            RootUtil.CommandResult result = RootUtil.executeRootCommand(command);
            mainHandler.post(() -> {
                if (result.isSuccess()) {
                    Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show();
                    refreshAllInfo(false);
                } else {
                    Toast.makeText(this, getString(R.string.action_failed, result.stderr), Toast.LENGTH_LONG).show();
                }
                mainHandler.postDelayed(this::startAutoRefresh, 1000);
            });
        });
    }

    private void applyPersistentConfig(final String interfaceName) {
        Set<String> ips = sharedPreferences.getStringSet(KEY_PERSISTENT_IPS, new HashSet<>());
        Set<String> routes = sharedPreferences.getStringSet(KEY_PERSISTENT_ROUTES, new HashSet<>());
        if (ips.isEmpty() && routes.isEmpty()) return;
        executorService.execute(() -> {
            for (String ip : ips) {
                RootUtil.executeRootCommand("ip addr add " + ip + " dev " + interfaceName);
            }
            for (String route : routes) {
                // +++ 修复：将持久化路由添加到正确的表中 +++
                RootUtil.executeRootCommand("ip route add " + route + " dev " + interfaceName + " table " + interfaceName);
            }
        });
    }

    private void saveIpToPrefs(String ipWithPrefix) {
        Set<String> ips = new HashSet<>(sharedPreferences.getStringSet(KEY_PERSISTENT_IPS, new HashSet<>()));
        ips.add(ipWithPrefix);
        sharedPreferences.edit().putStringSet(KEY_PERSISTENT_IPS, ips).apply();
    }

    private void removeIpFromPrefs(String ipWithPrefix) {
        Set<String> ips = new HashSet<>(sharedPreferences.getStringSet(KEY_PERSISTENT_IPS, new HashSet<>()));
        ips.remove(ipWithPrefix);
        sharedPreferences.edit().putStringSet(KEY_PERSISTENT_IPS, ips).apply();
    }

    private void saveRouteToPrefs(String routeCommandPart) {
        Set<String> routes = new HashSet<>(sharedPreferences.getStringSet(KEY_PERSISTENT_ROUTES, new HashSet<>()));
        routes.add(routeCommandPart);
        sharedPreferences.edit().putStringSet(KEY_PERSISTENT_ROUTES, routes).apply();
    }

    private void removeRouteFromPrefs(String fullRouteLine) {
        Set<String> routes = new HashSet<>(sharedPreferences.getStringSet(KEY_PERSISTENT_ROUTES, new HashSet<>()));
        String routeToRemove = null;
        for (String savedRoute : routes) {
            if (fullRouteLine.trim().contains(savedRoute)) {
                routeToRemove = savedRoute;
                break;
            }
        }
        if (routeToRemove != null) {
            routes.remove(routeToRemove);
            sharedPreferences.edit().putStringSet(KEY_PERSISTENT_ROUTES, routes).apply();
        }
    }

    private void clearAllPrefs() {
        sharedPreferences.edit().remove(KEY_PERSISTENT_IPS).remove(KEY_PERSISTENT_ROUTES).apply();
    }

    private void showAboutDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.about_title)
                .setMessage(R.string.about_message)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    private void showAddIpDialog() {
        if (currentInterfaceName.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_interface_detected), Toast.LENGTH_SHORT).show();
            return;
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.add_new_ip_title);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 20);
        final EditText ipInput = new EditText(this);
        ipInput.setHint(R.string.ip_address_hint);
        layout.addView(ipInput);
        final EditText prefixInput = new EditText(this);
        prefixInput.setHint(R.string.prefix_length_hint);
        prefixInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        layout.addView(prefixInput);
        builder.setView(layout);
        builder.setPositiveButton(R.string.add, (dialog, which) -> {
            String ip = ipInput.getText().toString().trim();
            String prefix = prefixInput.getText().toString().trim();
            if (!ip.isEmpty() && !prefix.isEmpty()) {
                String fullIp = ip + "/" + prefix;
                executeCommandAndRefresh("ip addr add " + fullIp + " dev " + currentInterfaceName, getString(R.string.ip_add_success));
                saveIpToPrefs(fullIp);
            }
        });
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showAddRouteDialog() {
        if (currentInterfaceName.isEmpty()) {
            Toast.makeText(this, getString(R.string.no_interface_detected), Toast.LENGTH_SHORT).show();
            return;
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle(R.string.add_new_route_title);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 20);
        final EditText destInput = new EditText(this);
        destInput.setHint(R.string.dest_subnet_hint);
        layout.addView(destInput);
        final CheckBox defaultCheck = new CheckBox(this);
        defaultCheck.setText(R.string.set_as_default_gateway);
        layout.addView(defaultCheck);
        final EditText gatewayInput = new EditText(this);
        gatewayInput.setHint(R.string.gateway_hint);
        layout.addView(gatewayInput);
        defaultCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            destInput.setEnabled(!isChecked);
            if (isChecked) destInput.setText("default");
            else destInput.setText("");
        });
        builder.setView(layout);
        builder.setPositiveButton(R.string.add, (dialog, which) -> {
            String dest = destInput.getText().toString().trim();
            String gateway = gatewayInput.getText().toString().trim();
            if (!dest.isEmpty() && !gateway.isEmpty()) {
                String routePart = dest + " via " + gateway;
                // +++ 修复：将手动添加的路由添加到正确的表中 +++
                String command = "ip route add " + routePart + " dev " + currentInterfaceName + " table " + currentInterfaceName;
                String successMessage = dest.equalsIgnoreCase("default") ? getString(R.string.default_gateway_set_success) : getString(R.string.route_add_success);

                executeCommandAndRefresh(command, successMessage);
                saveRouteToPrefs(routePart);
            }
        });
        builder.setNegativeButton(R.string.cancel, (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showDeleteConfirmationDialog(String itemType, String item, Runnable onConfirm) {
        String message = getString(R.string.confirm_delete_message, itemType, item);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.confirm_delete_title)
                .setMessage(message)
                .setPositiveButton(R.string.delete, (dialog, which) -> onConfirm.run())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void deleteIpAddress(String fullIp) {
        if (currentInterfaceName.isEmpty()) return;
        executeCommandAndRefresh("ip addr del " + fullIp + " dev " + currentInterfaceName, getString(R.string.ip_deleted_success));
        removeIpFromPrefs(fullIp);
    }

    private void deleteRoute(String fullRoute) {
        if (currentInterfaceName.isEmpty()) return;
        // +++ 修复：从正确的表中删除路由 +++
        String command = "ip route del " + fullRoute + " table " + currentInterfaceName;
        executeCommandAndRefresh(command, getString(R.string.route_deleted_success));
        removeRouteFromPrefs(fullRoute);
    }

    private void resetInterface() {
        if (currentInterfaceName.isEmpty()) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.confirm_reset_title)
                .setMessage(R.string.confirm_reset_message)
                .setPositiveButton(R.string.reset, (dialog, which) -> {
                    String command = "ip addr flush dev " + currentInterfaceName + " && " +
                            "ip link set " + currentInterfaceName + " down && " +
                            "sleep 1 && " +
                            "ip link set " + currentInterfaceName + " up";
                    executeCommandAndRefresh(command, getString(R.string.interface_reset_success));
                    clearAllPrefs();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}