package com.geekera1n.etherovo; // 确保包名一致

import android.content.Context;
import android.content.SharedPreferences;
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
import java.util.ArrayList;
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

    // --- 新增：用于跟踪接口的上一次状态 ---
    private boolean wasInterfaceUp = false;
    // --- 新增结束 ---

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
                showDeleteConfirmationDialog("IP地址", fullIp, () -> deleteIpAddress(fullIp));
                return true;
            });
            lvRoutes.setOnItemLongClickListener((parent, view, position, id) -> {
                String fullRoute = routeList.get(position).value;
                showDeleteConfirmationDialog("路由", fullRoute, () -> deleteRoute(fullRoute));
                return true;
            });
        }
    }

    private void updateUiState(boolean isConnected) {
        if (isConnected) {
            layoutConnected.setVisibility(View.VISIBLE);
            layoutDisconnected.setVisibility(View.GONE);
            boolean hasRoot = RootUtil.isRootAvailable();
            int visibility = hasRoot ? View.VISIBLE : View.GONE;
            btnAddIp.setVisibility(visibility);
            btnAddRoute.setVisibility(visibility);
            btnResetInterface.setVisibility(visibility);
            View ipTitle = findViewById(R.id.ip_section_title);
            if(ipTitle != null) ipTitle.setVisibility(visibility);
            View routeTitle = findViewById(R.id.route_section_title);
            if (routeTitle != null) routeTitle.setVisibility(visibility);
            lvIpAddresses.setVisibility(visibility);
            lvRoutes.setVisibility(visibility);
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
            Toast.makeText(this, "正在刷新...", Toast.LENGTH_SHORT).show();
        }
        executorService.execute(() -> {
            if (!RootUtil.isRootAvailable()) {
                mainHandler.post(() -> {
                    updateUiState(false);
                    Toast.makeText(this, "需要Root权限以检测接口", Toast.LENGTH_LONG).show();
                });
                return;
            }

            RootUtil.CommandResult resultLinks = RootUtil.executeRootCommand("ip link show");
            Pattern interfacePattern = Pattern.compile("\\d+: (eth\\d+|usb\\d+):");
            Matcher interfaceMatcher = interfacePattern.matcher(resultLinks.stdout);
            String foundInterfaceName = interfaceMatcher.find() ? interfaceMatcher.group(1) : "";

            if (foundInterfaceName.isEmpty()) {
                mainHandler.post(() -> {
                    this.currentInterfaceName = "";
                    this.wasInterfaceUp = false; // --- 修改：接口消失时，重置状态 ---
                    updateUiState(false);
                    if (showToast) Toast.makeText(this, "未找到USB有线网卡", Toast.LENGTH_SHORT).show();
                });
                return;
            }

            final boolean isNewInterfaceConnected = !foundInterfaceName.equals(this.currentInterfaceName);

            RootUtil.CommandResult resultLink = RootUtil.executeRootCommand("ip link show " + foundInterfaceName);
            String linkOutput = resultLink.stdout;

            // --- 修改：在这里提前获取 isUp 状态 ---
            boolean isUp = linkOutput.contains("state UP");

            // --- 修改：全新的、更完善的恢复逻辑 ---
            // 触发恢复的条件:
            // 1. 检测到一个全新的接口 (应对USB插拔)
            // 2. 或者，是同一个接口，但它刚刚从 DOWN 状态恢复到 UP 状态 (应对网线插拔)
            final boolean shouldRestoreConfig = isNewInterfaceConnected || (!this.wasInterfaceUp && isUp);

            if (shouldRestoreConfig) {
                mainHandler.post(() -> Toast.makeText(this, "检测到连接，正在恢复配置...", Toast.LENGTH_SHORT).show());
                applyPersistentConfig(foundInterfaceName);
                // 等待一小段时间让配置生效
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                // 重新获取信息以显示最新状态
                resultLink = RootUtil.executeRootCommand("ip link show " + foundInterfaceName);
                linkOutput = resultLink.stdout;
                isUp = linkOutput.contains("state UP");
            }
            // --- 修改结束 ---

            String macAddress = "N/A";
            Pattern macPattern = Pattern.compile("link/ether ([0-9a-fA-F:]+)");
            Matcher macMatcher = macPattern.matcher(linkOutput);
            if (macMatcher.find()) {
                macAddress = macMatcher.group(1);
            }

            RootUtil.CommandResult resultAddr = RootUtil.executeRootCommand("ip addr show " + foundInterfaceName);
            final List<InfoItem> newIpList = new ArrayList<>();
            Pattern ipPattern = Pattern.compile("inet (\\d+\\.\\d+\\.\\d+\\.\\d+/\\d+)");
            Matcher ipMatcher = ipPattern.matcher(resultAddr.stdout);
            while (ipMatcher.find()) {
                newIpList.add(new InfoItem("IPv4 地址", ipMatcher.group(1)));
            }

            RootUtil.CommandResult speedResult = RootUtil.executeRootCommand("cat /sys/class/net/" + foundInterfaceName + "/speed");
            String speed = "N/A";
            if (isUp && speedResult.isSuccess() && !speedResult.stdout.trim().isEmpty()) {
                try {
                    String speedValue = speedResult.stdout.trim();
                    if(Integer.parseInt(speedValue) > 0) {
                        speed = speedValue + " Mbps";
                    }
                } catch (NumberFormatException e) {
                    // Ignore
                }
            }

            RootUtil.CommandResult duplexResult = RootUtil.executeRootCommand("cat /sys/class/net/" + foundInterfaceName + "/duplex");
            String duplex = "N/A";
            if (isUp && duplexResult.isSuccess() && !duplexResult.stdout.trim().isEmpty()) {
                String d = duplexResult.stdout.trim();
                duplex = d.substring(0, 1).toUpperCase() + d.substring(1);
            }

            final List<InfoItem> newDetailsList = new ArrayList<>();
            newDetailsList.add(new InfoItem("MAC 地址", macAddress));
            newDetailsList.add(new InfoItem("状态", isUp ? "UP" : "DOWN"));
            newDetailsList.add(new InfoItem("速率", speed));
            newDetailsList.add(new InfoItem("双工模式", duplex));

            RootUtil.CommandResult resultRoute = RootUtil.executeRootCommand("ip route show dev " + foundInterfaceName);
            final List<InfoItem> newRouteList = new ArrayList<>();
            String[] lines = resultRoute.stdout.split("\\r?\\n");
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;

                String title;
                String cleanedLine = line.trim(); // 清理首尾空格

                if (cleanedLine.startsWith("default")) {
                    title = "默认路由 (网关)";
                } else {
                    // 对于其他路由，通常第一个词就是目标网络地址
                    String[] parts = cleanedLine.split("\\s+"); // 按一个或多个空格分割
                    if (parts.length > 0) {
                        // 创建一个如 "路由至 192.168.17.0/24" 的标题
                        title = "路由至 " + parts[0];
                    } else {
                        // 这是一个几乎不会发生的备用情况
                        title = "路由规则";
                    }
                }
                newRouteList.add(new InfoItem(title, cleanedLine));
            }
            final String finalInterfaceName = foundInterfaceName;
            final boolean finalIsUp = isUp; // --- 修改：将最终的状态传递给主线程 ---

            mainHandler.post(() -> {
                this.currentInterfaceName = finalInterfaceName;
                this.wasInterfaceUp = finalIsUp; // --- 修改：更新接口状态，为下一次刷新做准备 ---
                updateUiState(true);
                tvInterfaceName.setText(finalInterfaceName);

                updateListView(detailsList, newDetailsList, detailsAdapter, lvInterfaceDetails);
                updateListView(ipList, newIpList, ipAdapter, lvIpAddresses);
                updateListView(routeList, newRouteList, routeAdapter, lvRoutes);

                if (showToast) Toast.makeText(this, "刷新完成", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void updateListView(List<InfoItem> currentList, List<InfoItem> newList, InfoAdapter adapter, ListView listView) {
        if (!currentList.equals(newList)) {
            currentList.clear();
            currentList.addAll(newList);
            adapter.notifyDataSetChanged();
            setListViewHeightBasedOnChildren(listView);
        }
    }

    public static void setListViewHeightBasedOnChildren(ListView listView) {
        ListAdapter listAdapter = listView.getAdapter();
        if (listAdapter == null) return;
        int totalHeight = 0;
        for (int i = 0; i < listAdapter.getCount(); i++) {
            View listItem = listAdapter.getView(i, null, listView);
            listItem.measure(View.MeasureSpec.makeMeasureSpec(listView.getWidth(), View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            totalHeight += listItem.getMeasuredHeight();
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
            public void run() {
                refreshAllInfo(false);
                autoRefreshHandler.postDelayed(this, 2000);
            }
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
        Toast.makeText(this, "正在执行操作...", Toast.LENGTH_SHORT).show();
        executorService.execute(() -> {
            RootUtil.CommandResult result = RootUtil.executeRootCommand(command);
            mainHandler.post(() -> {
                if (result.isSuccess()) {
                    Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show();
                    refreshAllInfo(false);
                } else {
                    Toast.makeText(this, "操作失败:\n" + result.stderr, Toast.LENGTH_LONG).show();
                }
                mainHandler.postDelayed(this::startAutoRefresh, 1000);
            });
        });
    }

    private void applyPersistentConfig(final String interfaceName) {
        Set<String> ips = sharedPreferences.getStringSet(KEY_PERSISTENT_IPS, new HashSet<>());
        Set<String> routes = sharedPreferences.getStringSet(KEY_PERSISTENT_ROUTES, new HashSet<>());

        if (ips.isEmpty() && routes.isEmpty()) {
            return;
        }

        executorService.execute(() -> {
            for (String ip : ips) {
                RootUtil.executeRootCommand("ip addr add " + ip + " dev " + interfaceName);
            }

            for (String route : routes) {
                if (route.startsWith("default")) {
                    RootUtil.executeRootCommand("ip route del default dev " + interfaceName + "; ip route add " + route + " dev " + interfaceName);
                } else {
                    RootUtil.executeRootCommand("ip route add " + route + " dev " + interfaceName);
                }
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
            // "ip route show" 的输出 (fullRouteLine) 包含我们保存的路由命令部分 (savedRoute)
            if (fullRouteLine.trim().startsWith(savedRoute)) {
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
                .setTitle("关于 EtherOvO")
                .setMessage("这是一款高级USB有线网卡管理工具。\n\n由您和Gemini共同打造。")
                .setPositiveButton("确定", null)
                .show();
    }

    private void showAddIpDialog() {
        if (currentInterfaceName.isEmpty()) {
            Toast.makeText(this, "未检测到有效接口，请先刷新", Toast.LENGTH_SHORT).show();
            return;
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("添加新IP地址");
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 20);
        final EditText ipInput = new EditText(this);
        ipInput.setHint("IP 地址 (例如 192.168.12.6)");
        layout.addView(ipInput);
        final EditText prefixInput = new EditText(this);
        prefixInput.setHint("网络前缀长度 (例如 24)");
        prefixInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        layout.addView(prefixInput);
        builder.setView(layout);
        builder.setPositiveButton("添加", (dialog, which) -> {
            String ip = ipInput.getText().toString().trim();
            String prefix = prefixInput.getText().toString().trim();
            if (!ip.isEmpty() && !prefix.isEmpty()) {
                String fullIp = ip + "/" + prefix;
                executeCommandAndRefresh("ip addr add " + fullIp + " dev " + currentInterfaceName, "IP地址添加成功");
                saveIpToPrefs(fullIp);
            }
        });
        builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showAddRouteDialog() {
        if (currentInterfaceName.isEmpty()) {
            Toast.makeText(this, "未检测到有效接口，请先刷新", Toast.LENGTH_SHORT).show();
            return;
        }
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this);
        builder.setTitle("添加新路由");
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 20);
        final EditText destInput = new EditText(this);
        destInput.setHint("目标子网 (例如 10.0.0.0/8)");
        layout.addView(destInput);
        final CheckBox defaultCheck = new CheckBox(this);
        defaultCheck.setText("设为默认网关");
        layout.addView(defaultCheck);
        final EditText gatewayInput = new EditText(this);
        gatewayInput.setHint("通过网关 (例如 192.168.12.254)");
        layout.addView(gatewayInput);
        defaultCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            destInput.setEnabled(!isChecked);
            if(isChecked) destInput.setText("default");
            else destInput.setText("");
        });
        builder.setView(layout);
        builder.setPositiveButton("添加", (dialog, which) -> {
            String dest = destInput.getText().toString().trim();
            String gateway = gatewayInput.getText().toString().trim();
            if (!dest.isEmpty() && !gateway.isEmpty()) {
                String routePart = dest + " via " + gateway;
                String command;
                String successMessage;
                if (dest.equalsIgnoreCase("default")) {
                    command = "ip route del default; ip route add " + routePart + " dev " + currentInterfaceName;
                    successMessage = "默认网关已设置";
                } else {
                    command = "ip route add " + routePart + " dev " + currentInterfaceName;
                    successMessage = "路由添加成功";
                }
                executeCommandAndRefresh(command, successMessage);
                saveRouteToPrefs(routePart);
            }
        });
        builder.setNegativeButton("取消", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void showDeleteConfirmationDialog(String itemType, String item, Runnable onConfirm) {
        new MaterialAlertDialogBuilder(this)
                .setTitle("确认删除")
                .setMessage("您确定要删除这个" + itemType + "吗？\n\n" + item)
                .setPositiveButton("删除", (dialog, which) -> onConfirm.run())
                .setNegativeButton("取消", null)
                .show();
    }

    private void deleteIpAddress(String fullIp) {
        if (currentInterfaceName.isEmpty()) return;
        executeCommandAndRefresh("ip addr del " + fullIp + " dev " + currentInterfaceName, "IP地址已删除");
        removeIpFromPrefs(fullIp);
    }

    private void deleteRoute(String fullRoute) {
        if (currentInterfaceName.isEmpty()) return;
        executeCommandAndRefresh("ip route del " + fullRoute, "路由已删除");
        removeRouteFromPrefs(fullRoute);
    }

    private void resetInterface() {
        if (currentInterfaceName.isEmpty()) return;
        new MaterialAlertDialogBuilder(this)
                .setTitle("确认重置接口")
                .setMessage("这将清除所有手动配置的IP和路由，并让系统重新尝试DHCP。您确定吗？")
                .setPositiveButton("重置", (dialog, which) -> {
                    String command = "ip addr flush dev " + currentInterfaceName + " && " +
                            "ip link set " + currentInterfaceName + " down && " +
                            "sleep 1 && " +
                            "ip link set " + currentInterfaceName + " up";
                    executeCommandAndRefresh(command, "接口已重置");
                    clearAllPrefs();
                })
                .setNegativeButton("取消", null)
                .show();
    }
}