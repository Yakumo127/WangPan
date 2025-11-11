package com.filemanager.controller;

import com.filemanager.dto.UserLogDTO;
import com.filemanager.service.AuditLogQueryService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.io.ByteArrayOutputStream;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

@RestController
@RequestMapping("/api/admin/logs")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class AdminLogController {

    private final AuditLogQueryService queryService;

    @GetMapping
    public Page<UserLogDTO> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String actions,
            @RequestParam(required = false) String resourceTypes,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "createTime") String sort,
            @RequestParam(defaultValue = "desc") String order
    ) {
        Set<String> actionSet = split(actions);
        Set<String> rtypeSet = split(resourceTypes);
        Sort.Direction dir = "asc".equalsIgnoreCase(order) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort s = Sort.by(dir, sort == null || sort.isBlank() ? "createTime" : sort);
        return queryService.query(actionSet, rtypeSet, status, keyword, from, to, page, size, s);
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) String format,
            @RequestParam(required = false) String actions,
            @RequestParam(required = false) String resourceTypes,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "createTime") String sort,
            @RequestParam(defaultValue = "desc") String order,
            @RequestParam(defaultValue = "50000") int maxRows
    ) throws Exception {
        Set<String> actionSet = split(actions);
        Set<String> rtypeSet = split(resourceTypes);
        Sort.Direction dir = "asc".equalsIgnoreCase(order) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort s = Sort.by(dir, sort == null || sort.isBlank() ? "createTime" : sort);
        int limit = Math.max(1, Math.min(maxRows, 50000));
        List<UserLogDTO> rows = queryService.queryAllLimited(actionSet, rtypeSet, status, keyword, from, to, limit, s);

        String fmt = (format == null || format.isBlank()) ? "csv" : format.trim().toLowerCase();
        byte[] bytes;
        String filenameBase = "user-logs_" + java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(java.time.LocalDateTime.now());
        if ("xlsx".equals(fmt)) {
            bytes = toXlsx(rows);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filenameBase + ".xlsx")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(bytes);
        } else {
            bytes = toCsv(rows);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + filenameBase + ".csv")
                    .contentType(MediaType.TEXT_PLAIN)
                    .body(bytes);
        }
    }

    private byte[] toCsv(List<UserLogDTO> rows) {
        String[] headers = new String[]{"时间","用户名","昵称","动作","资源类型","资源ID","资源名","结果","耗时(ms)","IP","UA","错误"};
        StringBuilder sb = new StringBuilder();
        // BOM 以兼容Excel
        sb.append('\uFEFF');
        sb.append(String.join(",", headers)).append("\n");
        for (UserLogDTO r : rows) {
            sb.append(csv(r.getCreateTime()))
              .append(',').append(csv(r.getUsername()))
              .append(',').append(csv(r.getDisplayName()))
              .append(',').append(csv(r.getActionType()))
              .append(',').append(csv(r.getResourceType()))
              .append(',').append(csv(r.getResourceId()))
              .append(',').append(csv(r.getResourceName()))
              .append(',').append(csv(r.getStatus()))
              .append(',').append(csv(r.getExecutionTime()))
              .append(',').append(csv(r.getIpAddress()))
              .append(',').append(csv(r.getUserAgent()))
              .append(',').append(csv(r.getErrorMessage()))
              .append("\n");
        }
        return sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    private String csv(Object v) {
        if (v == null) return "";
        String s = String.valueOf(v);
        if (s.contains("\n") || s.contains(",") || s.contains("\"")) {
            s = '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    private byte[] toXlsx(List<UserLogDTO> rows) throws Exception {
        try (Workbook wb = new SXSSFWorkbook(100); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Logs");
            int r = 0;
            String[] headers = new String[]{"时间","用户名","昵称","动作","资源类型","资源ID","资源名","结果","耗时(ms)","IP","UA","错误"};
            Row h = sheet.createRow(r++);
            for (int i = 0; i < headers.length; i++) h.createCell(i).setCellValue(headers[i]);
            for (UserLogDTO row : rows) {
                Row rr = sheet.createRow(r++);
                int c = 0;
                rr.createCell(c++).setCellValue(row.getCreateTime() == null ? "" : row.getCreateTime().toString());
                rr.createCell(c++).setCellValue(nullToEmpty(row.getUsername()));
                rr.createCell(c++).setCellValue(nullToEmpty(row.getDisplayName()));
                rr.createCell(c++).setCellValue(nullToEmpty(row.getActionType()));
                rr.createCell(c++).setCellValue(nullToEmpty(row.getResourceType()));
                rr.createCell(c++).setCellValue(row.getResourceId() == null ? "" : String.valueOf(row.getResourceId()));
                rr.createCell(c++).setCellValue(nullToEmpty(row.getResourceName()));
                rr.createCell(c++).setCellValue(nullToEmpty(row.getStatus()));
                rr.createCell(c++).setCellValue(row.getExecutionTime() == null ? 0 : row.getExecutionTime());
                rr.createCell(c++).setCellValue(nullToEmpty(row.getIpAddress()));
                rr.createCell(c++).setCellValue(nullToEmpty(row.getUserAgent()));
                rr.createCell(c++).setCellValue(nullToEmpty(row.getErrorMessage()));
            }
            for (int i=0;i<headers.length;i++) sheet.autoSizeColumn(i);
            wb.write(bos);
            if (wb instanceof SXSSFWorkbook sx) { sx.dispose(); }
            return bos.toByteArray();
        }
    }

    private String nullToEmpty(String s) { return s == null ? "" : s; }

    private Set<String> split(String csv) {
        if (csv == null || csv.isBlank()) return null;
        return new HashSet<>(Arrays.asList(csv.split(",")));
    }

    @GetMapping("/actions")
    public java.util.Map<String, java.util.List<String>> listActions() {
        java.util.List<String> actions = java.util.List.of(
                com.filemanager.entity.UserLog.ACTION_LOGIN,
                com.filemanager.entity.UserLog.ACTION_UPLOAD,
                com.filemanager.entity.UserLog.ACTION_DOWNLOAD,
                com.filemanager.entity.UserLog.ACTION_DELETE,
                com.filemanager.entity.UserLog.ACTION_COPY,
                com.filemanager.entity.UserLog.ACTION_MOVE,
                com.filemanager.entity.UserLog.ACTION_RENAME,
                com.filemanager.entity.UserLog.ACTION_RESTORE,
                com.filemanager.entity.UserLog.ACTION_CREATE_FOLDER,
                com.filemanager.entity.UserLog.ACTION_DELETE_FOLDER,
                com.filemanager.entity.UserLog.ACTION_UPDATE_PROFILE,
                com.filemanager.entity.UserLog.ACTION_CHANGE_PASSWORD,
                com.filemanager.entity.UserLog.ACTION_ADMIN_SCHEDULE_DELETE,
                com.filemanager.entity.UserLog.ACTION_ADMIN_RESTORE,
                com.filemanager.entity.UserLog.ACTION_ADMIN_PURGE_EXPIRED,
                com.filemanager.entity.UserLog.ACTION_RECYCLE_REMOVE,
                com.filemanager.entity.UserLog.ACTION_RECYCLE_EMPTY
        );
        java.util.List<String> resourceTypes = java.util.List.of(
                com.filemanager.entity.UserLog.RESOURCE_FILE,
                com.filemanager.entity.UserLog.RESOURCE_FOLDER,
                com.filemanager.entity.UserLog.RESOURCE_USER,
                "API"
        );
        java.util.List<String> statuses = java.util.List.of(
                com.filemanager.entity.UserLog.STATUS_SUCCESS,
                com.filemanager.entity.UserLog.STATUS_FAILED
        );
        return java.util.Map.of(
                "actions", actions,
                "resourceTypes", resourceTypes,
                "statuses", statuses
        );
    }
}
