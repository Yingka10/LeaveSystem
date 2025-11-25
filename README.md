# 請假審核系統 (Leave Audit System)

## 專案簡介
本系統為「系統分析與設計」課程期末專題實作。
這是一個基於 Web 的請假管理系統，提供導師進行線上假單審核。系統具備自動化流程，包含審核通過/駁回後的 Email 通知功能，以及完整的後台審核紀錄 (Audit Log)。

## 系統架構
* **後端語言**：Java (JDK 17)
* **框架**：Spring Boot 3.5.8 (Spring Web, Spring Mail)
* **前端模板**：Thymeleaf
* **資料庫**：In-Memory Mock Data (模擬資料庫，重啟後重置)
* **開發工具**：VS Code / Maven

---

## 軟體需求 (Prerequisites)
在執行本系統前，請確保您的電腦已安裝：
1.  **Java JDK 17** 或以上版本。
2.  **Maven** (通常包含在 IDE 中)。
3.  **Git** (用於版本控制)。

---

## 安裝與設定 (Installation)

### 1. 下載專案
開啟終端機 (Terminal)，執行以下指令將專案 Clone 至本地：
```bash
git clone https://github.com/Yingka10/LeaveSystem.git
cd LeaveSystem
````

### 2\. 設定 Email 功能

為了資安考量，本專案的 `application.properties` 含有敏感密碼，因此未上傳至 GitHub。請依照以下步驟手動還原設定：

1.  進入 `src/main/resources/` 資料夾。
2.  將 `application.properties.example` 複製一份，並改名為 `application.properties`。
3.  打開 `application.properties`，填入您的 Gmail 應用程式密碼。

### 3\. 如何執行

1.  使用 **VS Code** 開啟 `LeaveSystem` 資料夾。
2.  等待 Maven 自動下載依賴套件。
3.  打開 `src/main/java/com/example/leaveapp/LeaveApplication.java`。
4.  點擊程式碼上方的 **Run** 按鈕。
5.  等待終端機出現 `Tomcat started on port 8080` 字樣。

系統啟動後，請開啟瀏覽器訪問： http://localhost:8080

#### 1\. 登入系統

  * **預設導師帳號**：`teacher`
  * **預設密碼**：`1234`

