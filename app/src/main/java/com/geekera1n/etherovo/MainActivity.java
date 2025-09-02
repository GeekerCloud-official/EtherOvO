package com.geekera1n.etherovo; // 确保包名一致

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
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
    private ListView lvIpAddresses, lvRoutes;
    private ArrayAdapter<String> ipAdapter, routeAdapter;
    private final List<String> ipList = new ArrayList<>();
    private final List<String> routeList = new ArrayList<>();
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
        // 首次启动时，手动刷新一次
        refreshAllInfo(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 当App返回前台时，（重新）开始自动刷新
        startAutoRefresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 当App进入后台时，停止自动刷新以节省电量
        stopAutoRefresh();
    }

    private void initViews() {
        layoutDisconnected = findViewById(R.id.layoutDisconnected);
        layoutConnected = findViewById(R.id.layoutConnected);
        topAppBar = findViewById(R.id.topAppBar);
        tvInterfaceName = findViewById(R.id.tvInterfaceName);
        btnAddIp = findViewById(R.id.btnAddIp);
        btnAddRoute = findViewById(R.id.btnAddRoute);
        btnResetInterface = findViewById(R.id.btnResetInterface);
        lvIpAddresses = findViewById(R.id.lvIpAddresses);
        lvRoutes = findViewById(R.id.lvRoutes);
        ipAdapter = new ArrayAdapter<>(this, R.layout.list_item_custom, android.R.id.text1, ipList);
        routeAdapter = new ArrayAdapter<>(this, R.layout.list_item_custom, android.R.id.text1, routeList);
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

        // 假设用户一定有Root权限
        btnAddIp.setOnClickListener(v -> showAddIpDialog());
        btnAddRoute.setOnClickListener(v -> showAddRouteDialog());
        btnResetInterface.setOnClickListener(v -> resetInterface());
        lvIpAddresses.setOnItemLongClickListener((parent, view, position, id) -> {
            String ipToDelete = ipList.get(position);
            showDeleteConfirmationDialog("IP地址", ipToDelete, () -> deleteIpAddress(ipToDelete));
            return true;
        });
        lvRoutes.setOnItemLongClickListener((parent, view, position, id) -> {
            String routeToDelete = routeList.get(position);
            showDeleteConfirmationDialog("路由", routeToDelete, () -> deleteRoute(routeToDelete));
            return true;
        });
    }

    private void updateUiState(boolean isConnected) {
        if (isConnected) {
            layoutConnected.setVisibility(View.VISIBLE);
            layoutDisconnected.setVisibility(View.GONE);
        } else {
            layoutConnected.setVisibility(View.GONE);
            layoutDisconnected.setVisibility(View.VISIBLE);
        }
    }

    // --- 这是全新的、最精简、最可靠的刷新逻辑 ---
    private void refreshAllInfo(boolean showToast) {
        if (showToast) {
            Toast.makeText(this, "正在刷新...", Toast.LENGTH_SHORT).show();
        }
        executorService.execute(() -> {
            // 步骤 1: 查找接口
            RootUtil.CommandResult resultLink = RootUtil.executeRootCommand("ip link show");
            Pattern patternLink = Pattern.compile("\\d+: (eth\\d+|usb\\d+):");
            Matcher matcherLink = patternLink.matcher(resultLink.stdout);
            String foundInterfaceName = matcherLink.find() ? matcherLink.group(1) : "";

            if (foundInterfaceName.isEmpty()) {
                // 如果找不到接口，就在主线程更新UI为“已断开”状态
                mainHandler.post(() -> {
                    this.currentInterfaceName = "";
                    updateUiState(false);
                    if (showToast) Toast.makeText(this, "未找到USB有线网卡", Toast.LENGTH_SHORT).show();
                });
                return;
            }

            // 如果找到了接口，就在主线程更新UI为“已连接”状态，并保存接口名
            mainHandler.post(() -> {
                this.currentInterfaceName = foundInterfaceName;
                updateUiState(true);
            });

            // 步骤 2: 获取IP地址
            RootUtil.CommandResult resultIp = RootUtil.executeRootCommand("ip -4 a show dev " + foundInterfaceName);
            final List<String> newIpList = new ArrayList<>();
            Pattern patternIp = Pattern.compile("inet (\\d+\\.\\d+\\.\\d+\\.\\d+/\\d+)");
            Matcher matcherIp = patternIp.matcher(resultIp.stdout);
            while (matcherIp.find()) {
                newIpList.add(matcherIp.group(1));
            }

            // 步骤 3: 获取路由
            RootUtil.CommandResult resultRoute = RootUtil.executeRootCommand("ip route show dev " + foundInterfaceName);
            final List<String> newRouteList = new ArrayList<>();
            String[] lines = resultRoute.stdout.split("\\r?\\n");
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    newRouteList.add(line.trim());
                }
            }

            // 步骤 4: 在主线程更新列表内容
            mainHandler.post(() -> {
                tvInterfaceName.setText("接口: " + this.currentInterfaceName);
                if (!ipList.equals(newIpList)) {
                    ipList.clear();
                    ipList.addAll(newIpList);
                    ipAdapter.notifyDataSetChanged();
                }
                if (!routeList.equals(newRouteList)) {
                    routeList.clear();
                    routeList.addAll(newRouteList);
                    routeAdapter.notifyDataSetChanged();
                }
                if (showToast) Toast.makeText(this, "刷新完成", Toast.LENGTH_SHORT).show();
            });
        });
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
                    // 关键：在手动操作成功后，立即发起一次高质量的刷新
                    refreshAllInfo(false);
                } else {
                    Toast.makeText(this, "操作失败:\n" + result.stderr, Toast.LENGTH_LONG).show();
                }
                // 无论成功与否，都恢复自动刷新
                mainHandler.postDelayed(this::startAutoRefresh, 1000);
            });
        });
    }

    // --- 所有弹窗和删除方法保持不变 ---
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