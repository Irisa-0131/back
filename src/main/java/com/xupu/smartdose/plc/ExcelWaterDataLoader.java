package com.xupu.smartdose.plc;

import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 从 Excel 文件加载历史监测数据，循环提供给 MockPlcDataService。
 *
 * Excel 列索引（0-based，第1行为表头，数据从第2行起）：
 *   0  = Time
 *   1  = COD_in      2  = NH3_in     3  = TN_in     4  = TP_in
 *   13 = Flow_out
 *   14 = NH3_out     15 = COD_out    16 = TP_out     17 = TN_out
 */
@Slf4j
@Component
public class ExcelWaterDataLoader {

    @Value("${mock.plc.excel-path}")
    private String excelPath;

    /**
     * 每行存储：[NH3_in, COD_in, TN_in, TP_in, Flow_out, NH3_out, COD_out, TP_out, TN_out]
     * 索引:       0        1       2      3       4          5        6        7       8
     */
    private final List<double[]> rows = new ArrayList<>();
    private final AtomicInteger cursor = new AtomicInteger(0);

    @PostConstruct
    public void load() {
        File file = new File(excelPath);
        if (!file.exists()) {
            log.warn("[ExcelLoader] 文件不存在，MockPLC 将回退到随机数据: {}", excelPath);
            return;
        }
        try (var wb = WorkbookFactory.create(file)) {
            Sheet sheet = wb.getSheetAt(0);
            int last = sheet.getLastRowNum();
            for (int i = 1; i <= last; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                double[] data = new double[9];
                data[0] = num(row, 2);   // NH3_in
                data[1] = num(row, 1);   // COD_in
                data[2] = num(row, 3);   // TN_in
                data[3] = num(row, 4);   // TP_in
                data[4] = num(row, 13);  // Flow_out
                data[5] = num(row, 14);  // NH3_out
                data[6] = num(row, 15);  // COD_out
                data[7] = num(row, 16);  // TP_out
                data[8] = num(row, 17);  // TN_out
                rows.add(data);
            }
            log.info("[ExcelLoader] 加载完成，共 {} 行数据，路径: {}", rows.size(), excelPath);
        } catch (Exception e) {
            log.error("[ExcelLoader] 读取 Excel 失败: {}", e.getMessage());
        }
    }

    /** 取下一行，到底后自动回绕到第一行 */
    public double[] nextRow() {
        if (rows.isEmpty()) return null;
        int idx = cursor.getAndUpdate(i -> (i + 1) % rows.size());
        return rows.get(idx);
    }

    public boolean hasData() {
        return !rows.isEmpty();
    }

    private double num(Row row, int col) {
        Cell cell = row.getCell(col);
        if (cell == null) return 0.0;
        return switch (cell.getCellType()) {
            case NUMERIC -> cell.getNumericCellValue();
            case STRING  -> {
                try { yield Double.parseDouble(cell.getStringCellValue().trim()); }
                catch (NumberFormatException e) { yield 0.0; }
            }
            default -> 0.0;
        };
    }
}
