package com.example.leaveapp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@Controller
public class LeaveController {

    @Autowired(required = false) // 允許為 null，避免沒設定 email 時報錯
    private JavaMailSender mailSender;

    private static List<LeaveRequest> leaveDb = new ArrayList<>();
    // 新增：存放審核紀錄的 List
    private static List<AuditLog> auditLogs = new ArrayList<>();

    static {
        leaveDb.add(new LeaveRequest("L001", "周佳穎", "112403508", "事假", "2025-12-01", "家裡有事", "待審核", "jjjj65034666@gmail.com"));
        leaveDb.add(new LeaveRequest("L002", "王小明", "112400001", "病假", "2025-12-02", "感冒發燒", "待審核", "test2@example.com"));
        leaveDb.add(new LeaveRequest("L003", "陳大文", "112400002", "公假", "2025-12-03", "程式競賽", "待審核", "test3@example.com"));
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
        LeaveRequest leave = leaveDb.stream().filter(l -> l.getId().equals(id)).findFirst().orElse(null);
        model.addAttribute("leave", leave);
        return "detail";
    }

    // 新增：查看審核紀錄頁面
    @GetMapping("/audit-logs")
    public String viewAuditLogs(Model model) {
        model.addAttribute("logs", auditLogs);
        return "logs"; // 對應 logs.html
    }

    // 單筆審核
    @PostMapping("/leaves/{id}/audit")
    public String auditSingle(@PathVariable String id, @RequestParam String action) {
        LeaveRequest leave = leaveDb.stream().filter(l -> l.getId().equals(id)).findFirst().orElse(null);
        if (leave != null) {
            processAudit(leave, action);
        }
        // 審核完直接跳轉到紀錄頁面 (或留列表頁也可)
        return "redirect:/audit-logs"; 
    }

    // 批次處理
    @PostMapping("/leaves/batch")
    public String auditBatch(@RequestParam(required = false) List<String> ids, 
                             @RequestParam String action, 
                             Model model) {
        
        // 防呆：如果沒選任何東西就按按鈕，直接回列表
        if (ids == null || ids.isEmpty()) {
            return "redirect:/leaves";
        }

        // 情況 A：如果是按「檢視選取明細」 (New Feature)
        if ("review".equals(action)) {
            // 找出所有被勾選的 LeaveRequest 物件
            List<LeaveRequest> selectedLeaves = new ArrayList<>();
            for (String id : ids) {
                leaveDb.stream()
                        .filter(l -> l.getId().equals(id))
                        .findFirst()
                        .ifPresent(selectedLeaves::add);
            }
            // 把這些假單丟給前端 model
            model.addAttribute("selectedLeaves", selectedLeaves);
            return "review"; //導向 review.html
        }

        // 情況 B：如果是按「直接全部通過/不通過」 (Existing Feature)
        for (String id : ids) {
            LeaveRequest leave = leaveDb.stream().filter(l -> l.getId().equals(id)).findFirst().orElse(null);
            if (leave != null) {
                processAudit(leave, action); // 呼叫原本寫好的審核函式
            }
        }
        return "redirect:/audit-logs";
    }

    // --- 封裝好的處理邏輯：更新狀態 + 寫紀錄 + 寄信 ---
    private void processAudit(LeaveRequest leave, String action) {
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
        auditLogs.add(0, new AuditLog(leave.getId(), leave.getStudentName(), logAction, "Teacher")); // 加在最前面

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