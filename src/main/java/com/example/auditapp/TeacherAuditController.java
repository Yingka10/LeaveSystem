package com.example.auditapp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;

@Controller
public class TeacherAuditController {

    @Autowired(required = false) // 允許為 null，避免沒設定 email 時報錯
    private JavaMailSender mailSender;

    private static List<StudentLeaveForm> leaveDb = new ArrayList<>();
    // 新增：存放審核紀錄的 List
    private static List<AuditLog> auditRecords = new ArrayList<>();

    static {
        // 模擬檔案連結 (這裡用假網址示意，點擊會開新分頁)
        String demoFileUrl = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf";
        String demoImgUrl = "https://via.placeholder.com/600x400.png?text=Proof+Document";

        // 資料 1: 體育課請假 (兩節課)
        List<StudentLeaveForm.ScheduleItem> schedule1 = new ArrayList<>();
        schedule1.add(new StudentLeaveForm.ScheduleItem("2025-11-25", "星期二", "5", "PE2071 排球入門", "陳政達"));
        schedule1.add(new StudentLeaveForm.ScheduleItem("2025-11-25", "星期二", "6", "PE2071 排球入門", "陳政達"));

        leaveDb.add(new StudentLeaveForm("L001", "周佳穎", "112403508", "事假", "家裡有事需返鄉", "待審核", "test1@example.com", 
                schedule1, null, demoFileUrl)); // 證明文件無，但導師同意書有(必填)

        // 資料 2: 微積分請假 (一節課)
        List<StudentLeaveForm.ScheduleItem> schedule2 = new ArrayList<>();
        schedule2.add(new StudentLeaveForm.ScheduleItem("2025-12-02", "星期二", "3", "IM1001 微積分", "林數學"));

        leaveDb.add(new StudentLeaveForm("L002", "王小明", "112400001", "病假", "感冒發燒", "待審核", "test2@example.com", 
                schedule2, demoImgUrl, demoFileUrl)); // 都有附檔案
        
        // 資料 3: 公假
        List<StudentLeaveForm.ScheduleItem> schedule3 = new ArrayList<>();
        schedule3.add(new StudentLeaveForm.ScheduleItem("2025-12-03", "星期三", "1", "CS101 程式設計", "張資工"));
        
        leaveDb.add(new StudentLeaveForm("L003", "陳大文", "112400002", "公假", "參加程式競賽", "待審核", "test3@example.com", 
                schedule3, demoFileUrl, demoFileUrl));
    }


    @GetMapping("/")
    public String loginPage() { return "login"; }

    @PostMapping("/login")
    public String doLogin(@RequestParam String username, @RequestParam String password, Model model) {
        if ("teacher".equals(username) && "1234".equals(password)) {
            return "redirect:/leaves";
        } else {
            model.addAttribute("error", "帳號或密碼錯誤");
            return "login";
        }
    }
    
    @GetMapping("/logout")
    public String logout() { return "redirect:/"; }

    // 列表頁
    @GetMapping("/leaves")
    public String listLeaves(Model model) {
        model.addAttribute("leaves", leaveDb);
        return "list";
    }

    // 詳細頁
    @GetMapping("/leaves/{id}")
    public String leaveDetail(@PathVariable String id, Model model) {
        StudentLeaveForm leave = leaveDb.stream().filter(l -> l.getId().equals(id)).findFirst().orElse(null);
        model.addAttribute("leave", leave);
        return "detail";
    }

    // 新增：查看審核紀錄頁面
    @GetMapping("/audit-logs")
    public String viewAuditLogs(Model model) {
        model.addAttribute("logs", auditRecords);
        return "logs"; // 對應 logs.html
    }

    // 單筆審核
    @PostMapping("/leaves/{id}/audit")
    public String auditSingle(@PathVariable String id, 
                              @RequestParam String action,
                              RedirectAttributes redirectAttributes) { 
        StudentLeaveForm leave = leaveDb.stream().filter(l -> l.getId().equals(id)).findFirst().orElse(null);
        
        if (leave != null) {
            processAudit(leave, action);
            // 設定快閃訊息 (Flash Attribute)，只會在下一次頁面顯示一次
            redirectAttributes.addFlashAttribute("message", "單號 " + id + " 審核已完成，並已寄送通知信！");
        }
        
        return "redirect:/leaves"; // 導回列表
    }

    // 批次處理
    @PostMapping("/leaves/batch")
    public String auditBatch(@RequestParam(required = false) List<String> ids, 
                             @RequestParam String action, 
                             Model model,
                             RedirectAttributes redirectAttributes) { // 加入這個參數
        
        if (ids == null || ids.isEmpty()) {
             // 如果後端擋下，也給個錯誤訊息
            redirectAttributes.addFlashAttribute("error", "請至少勾選一筆！");
            return "redirect:/leaves";
        }

        // 情況 A：檢視選取明細 (這個不用跳通知，直接導向頁面)
        if ("review".equals(action)) {
            List<StudentLeaveForm> selectedLeaves = new ArrayList<>();
            for (String id : ids) {
                leaveDb.stream().filter(l -> l.getId().equals(id)).findFirst().ifPresent(selectedLeaves::add);
            }
            model.addAttribute("selectedLeaves", selectedLeaves);
            return "review";
        }

        // 情況 B：直接全部通過/不通過
        for (String id : ids) {
            StudentLeaveForm leave = leaveDb.stream().filter(l -> l.getId().equals(id)).findFirst().orElse(null);
            if (leave != null) {
                processAudit(leave, action);
            }
        }
        
        // 設定成功訊息
        String msgAction = "approve".equals(action) ? "通過" : "不通過";
        redirectAttributes.addFlashAttribute("message", "已將選取的 " + ids.size() + " 筆假單全部" + msgAction + "，並已寄送通知信！");

        return "redirect:/leaves";
    }

    // --- 封裝好的處理邏輯：更新狀態 + 寫紀錄 + 寄信 ---
    private void processAudit(StudentLeaveForm leave, String action) {
        String resultStatus = "";
        String subject = "";
        String body = "";
        String logAction = "";

        if ("approve".equals(action)) {
            resultStatus = "通過";
            logAction = "核准";
            subject = "【假單通知】您的假單已核准";
            body = "親愛的 " + leave.getStudentName() + " 同學：\n\n您的假單 (單號: " + leave.getId() + ") 已經通過審核。\n\n導師 Teacher";
        } else if ("reject".equals(action)) {
            resultStatus = "不通過";
            logAction = "駁回";
            subject = "【假單通知】您的假單未核准";
            body = "親愛的 " + leave.getStudentName() + " 同學：\n\n很遺憾，您的假單 (單號: " + leave.getId() + ") 未通過審核。\n請與導師聯繫。\n\n導師 Teacher";
        }

        // 1. 更新狀態
        leave.setStatus(resultStatus);

        // 2. 寫入審核紀錄
        auditRecords.add(0, new AuditLog(leave.getId(), leave.getStudentName(), logAction, "Teacher")); // 加在最前面

        // 3. 寄送 Email (包含防呆機制)
        sendEmailSafely(leave.getEmail(), subject, body);
    }

    private void sendEmailSafely(String to, String subject, String text) {
        try {
            if (mailSender != null) {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(to);
                message.setSubject(subject);
                message.setText(text);
                message.setFrom("your-email@gmail.com"); // 記得改成你的
                mailSender.send(message);
                System.out.println("Email 發送成功至: " + to);
            } else {
                System.out.println(">> 模擬寄信 (未設定 MailSender): To " + to + " | " + subject);
            }
        } catch (Exception e) {
            System.err.println("Email 發送失敗: " + e.getMessage());
            // 失敗不中斷程式，讓演示繼續
        }
    }
}