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
// 删除了旧的 AlertDialog 引用
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.appbar.MaterialToolbar;
// 这是关键的新增引用！
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    // ... (所有变量定义和 onCreate, initViews, setupListeners 等方法保持不变) ...
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initViews();
        setupListeners();
        refreshAllInfo();
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
                refreshAllInfo();
                return true;
            }
            if (item.getItemId() == R.id.action_about) {
                showAboutDialog();
                return true;
            }
            return false;
        });
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

    private void refreshAllInfo() {
        Toast.makeText(this, "正在刷新...", Toast.LENGTH_SHORT).show();
        executorService.execute(() -> {
            RootUtil.CommandResult resultLink = RootUtil.executeRootCommand("ip link show");
            Pattern patternLink = Pattern.compile("\\d+: (eth\\d+|usb\\d+):");
            Matcher matcherLink = patternLink.matcher(resultLink.stdout);
            currentInterfaceName = matcherLink.find() ? matcherLink.group(1) : "";
            boolean isUp = !currentInterfaceName.isEmpty() && resultLink.stdout.contains(currentInterfaceName + ":") && resultLink.stdout.contains("state UP");
            if (!isUp) {
                mainHandler.post(() -> {
                    updateUiState(false);
                    Toast.makeText(this, "未找到已连接的网卡", Toast.LENGTH_SHORT).show();
                });
                return;
            }
            mainHandler.post(() -> updateUiState(true));
            RootUtil.CommandResult resultIp = RootUtil.executeRootCommand("ip -4 a show dev " + currentInterfaceName);
            List<String> newIpList = new ArrayList<>();
            Pattern patternIp = Pattern.compile("inet (\\d+\\.\\d+\\.\\d+\\.\\d+/\\d+)");
            Matcher matcherIp = patternIp.matcher(resultIp.stdout);
            while (matcherIp.find()) {
                newIpList.add(matcherIp.group(1));
            }
            RootUtil.CommandResult resultRoute = RootUtil.executeRootCommand("ip route show dev " + currentInterfaceName);
            List<String> newRouteList = new ArrayList<>();
            String[] lines = resultRoute.stdout.split("\\r?\\n");
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    newRouteList.add(line.trim());
                }
            }
            mainHandler.post(() -> {
                tvInterfaceName.setText("接口: " + currentInterfaceName);
                ipList.clear();
                ipList.addAll(newIpList);
                ipAdapter.notifyDataSetChanged();
                routeList.clear();
                routeList.addAll(newRouteList);
                routeAdapter.notifyDataSetChanged();
                Toast.makeText(this, "刷新完成", Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void showAboutDialog() {
        // 使用 MaterialAlertDialogBuilder
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
        // 使用 MaterialAlertDialogBuilder
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
        // 使用 MaterialAlertDialogBuilder
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
        // 使用 MaterialAlertDialogBuilder
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
        // 使用 MaterialAlertDialogBuilder
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

    private void executeCommandAndRefresh(String command, String successMessage) {
        executorService.execute(() -> {
            RootUtil.CommandResult result = RootUtil.executeRootCommand(command);
            mainHandler.post(() -> {
                if (result.isSuccess()) {
                    Toast.makeText(this, successMessage, Toast.LENGTH_SHORT).show();
                    mainHandler.postDelayed(this::refreshAllInfo, 500);
                } else {
                    Toast.makeText(this, "操作失败:\n" + result.stderr, Toast.LENGTH_LONG).show();
                }
            });
        });
    }
}