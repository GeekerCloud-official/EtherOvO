package com.geekera1n.etherovo; // 确保包名一致

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
import java.util.List;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
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
                    updateUiState(false);
                    if (showToast) Toast.makeText(this, "未找到USB有线网卡", Toast.LENGTH_SHORT).show();
                });
                return;
            }

            RootUtil.CommandResult resultLink = RootUtil.executeRootCommand("ip link show " + foundInterfaceName);
            String linkOutput = resultLink.stdout;

            String macAddress = "N/A";
            Pattern macPattern = Pattern.compile("link/ether ([0-9a-fA-F:]+)");
            Matcher macMatcher = macPattern.matcher(linkOutput);
            if (macMatcher.find()) {
                macAddress = macMatcher.group(1);
            }
            boolean isUp = linkOutput.contains("state UP");

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
                if (!line.trim().isEmpty()) {
                    String title = line.startsWith("default") ? "默认路由 (网关)" : "" + line.split("路由至 ")[0];
                    newRouteList.add(new InfoItem(title, line));
                }
            }

            final String finalInterfaceName = foundInterfaceName;

            mainHandler.post(() -> {
                this.currentInterfaceName = finalInterfaceName;
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
                executeCommandAndRefresh("ip addr add " + ip + "/" + prefix + " dev " + currentInterfaceName, "IP地址添加成功");
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
                if (dest.equalsIgnoreCase("default")) {
                    String command = "ip route del default; ip route add default via " + gateway + " dev " + currentInterfaceName;
                    executeCommandAndRefresh(command, "默认网关已设置");
                } else {
                    executeCommandAndRefresh("ip route add " + dest + " via " + gateway + " dev " + currentInterfaceName, "路由添加成功");
                }
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
    }

    private void deleteRoute(String fullRoute) {
        if (currentInterfaceName.isEmpty()) return;
        executeCommandAndRefresh("ip route del " + fullRoute, "路由已删除");
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
                })
                .setNegativeButton("取消", null)
                .show();
    }
}