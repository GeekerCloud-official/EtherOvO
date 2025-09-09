# EtherOvO

**English** | [**中文**](#etherovo-中文版)

An advanced management tool for USB Ethernet adapters on Android devices, featuring dual-mode operation for both Root and Non-Root users.

<p align="center">
  <img src="https://github.com/GeekerCloud-official/EtherOvO/blob/main/imgs/app-icon.png" alt="App Icon" width="128"/>
</p>

[![API](https://img.shields.io/badge/API-24%2B-brightgreen.svg?style=flat)](https://android-arsenal.com/api?level=24)
[![License: GPL-3.0](https://img.shields.io/badge/license-GPL--3.0-orange.svg)](https://www.gnu.org/licenses/gpl-3.0)

---

## Screenshots / 软件截图

| Connected (Root Mode) / 连接状态 (Root 模式) | Connected (Non-Root Mode) / 连接状态 (非Root 模式) | Disconnected / 未连接状态 |
| :----------------------------------------------------------: | :----------------------------------------------------------: | :----------------------------------------------------------: |
| ![Screenshot of Root Mode](https://github.com/GeekerCloud-official/EtherOvO/blob/main/imgs/Root_Mode.jpg) | ![Screenshot of Non-Root Mode](https://github.com/GeekerCloud-official/EtherOvO/blob/main/imgs/Non_Root_Mode.jpg) | ![Screenshot of Disconnected State](https://github.com/GeekerCloud-official/EtherOvO/blob/main/imgs/Disconnect.jpg) |

## Features / 功能特性

EtherOvO intelligently detects if Root access is available and adjusts its functionality accordingly.

#### With Root Access (Full Functionality)
*   **View Detailed Status**: Get real-time information including MAC address, link speed, duplex mode, DNS servers, IP addresses, and routes.
*   **Dynamic IP & Route Management**: Easily add or delete multiple IPv4 addresses and routing rules on the fly.
*   **Persistent Configuration**: Manually configured IPs and routes are automatically saved and reapplied when the network adapter is reconnected or the link state changes (UP/DOWN).
*   **Interface Reset**: A one-click option to flush all configurations and restart the interface, allowing the system to re-attempt DHCP.
*   **Comprehensive Information**: Combines data from low-level `ip` commands and the Android `ConnectivityManager` API to provide the complete network overview.

#### Without Root Access (Read-Only Mode)
*   **Safe, Read-Only Information**: Securely view essential network information using standard Android APIs.
*   **View Core Status**: Displays the interface name, MAC address, assigned IPv4 addresses, default gateway, and DNS servers.
*   **Automatic Detection**: Reliably detects any active wired Ethernet connection, not just USB adapters.
*   **User-Friendly**: All modification features are hidden to provide a clean and simple viewing experience.

## How It Works / 工作原理

The application's core logic is built around a dual-mode system:

1.  **Root Mode**: When Root access is detected, EtherOvO leverages powerful shell commands (`ip link`, `ip addr`, `ip route`, `getprop`) to directly query and configure the network interface. This allows for advanced features like setting static IPs and managing persistent configurations.
2.  **Non-Root Mode**: If Root access is not available, the app gracefully falls back to using Android's standard `ConnectivityManager` API. This provides a safe, secure, and permission-compliant way to read network properties without requiring elevated privileges.

## Building from Source / 从源码构建

1.  Clone the repository:
    ```bash
    git clone https://github.com/GeekerCloud-official/EtherOvO.git
    ```
2.  Open the project in the latest stable version of Android Studio.
3.  Let Gradle sync and download the required dependencies.
4.  Build the project using `Build > Make Project` or generate an APK via `Build > Build Bundle(s) / APK(s) > Build APK(s)`.


---
<br>

# EtherOvO (中文版)

一款专为安卓设备打造的高级USB有线网卡管理工具，为 Root 和非 Root 用户提供双模式操作。

## 功能特性 / Features

EtherOvO 会智能检测设备是否拥有 Root 权限，并自动调整其功能。

#### Root 模式 (完整功能)
*   **查看详细状态**: 获取包括 MAC 地址、连接速率、双工模式、DNS 服务器、IP 地址和路由在内的实时信息。
*   **动态 IP 与路由管理**: 无需重启，轻松地在线添加或删除多个 IPv4 地址和路由规则。
*   **持久化配置**: 手动配置的 IP 和路由规则会被自动保存，并在网卡重新连接或链路状态改变 (UP/DOWN) 时自动恢复。
*   **重置接口**: 提供一键重置功能，可清除所有手动配置并重启网卡，让系统重新尝试通过 DHCP 获取地址。
*   **全面的信息整合**: 结合了底层的 `ip` 命令和安卓 `ConnectivityManager` API，为您提供完整的网络状态概览。

#### 非 Root 模式 (只读模式)
*   **安全只读信息**: 使用标准的安卓系统 API，安全地查看核心网络信息。
*   **查看核心状态**: 显示接口名称、MAC 地址、已分配的 IPv4 地址、默认网关和 DNS 服务器。
*   **自动检测**: 可靠地检测任何处于活动状态的有线以太网连接，不仅仅局限于 USB 网卡。
*   **简洁易用**: 所有修改功能的控件都会被自动隐藏，提供一个干净、纯粹的信息查阅界面。

## 工作原理 / How It Works

应用的核心逻辑构建于一个双模式系统之上：

1.  **Root 模式**: 当检测到 Root 权限时，EtherOvO 会利用强大的 Shell 命令 (`ip link`, `ip addr`, `ip route`, `getprop`) 来直接查询和配置网络接口。这使得设置静态 IP、管理持久化配置等高级功能成为可能。
2.  **非 Root 模式**: 如果没有 Root 权限，应用会平滑地降级，转而使用安卓标准的 `ConnectivityManager` API。这提供了一种安全、可靠且符合系统权限规范的方式来读取网络属性，无需任何特殊权限。

## 从源码构建 / Building from Source

1.  克隆本仓库:
    ```bash
    git clone https://github.com/GeekerCloud-official/EtherOvO.git
    ```
2.  在最新稳定版的 Android Studio 中打开此项目。
3.  等待 Gradle 完成同步并下载所需依赖。
4.  通过 `Build > Make Project` 构建项目，或通过 `Build > Build Bundle(s) / APK(s) > Build APK(s)` 生成 APK 文件。

