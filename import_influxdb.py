"""
将监测数据_25.xlsx 前 4380 条（前半年）写入 InfluxDB
"""
import openpyxl
from influxdb_client import InfluxDBClient, Point
from influxdb_client.client.write_api import SYNCHRONOUS
from datetime import timezone

# InfluxDB 配置（与 application.yml 保持一致）
INFLUX_URL   = "http://localhost:8086"
INFLUX_TOKEN = "9XxXWKVi-4nqb2pMOigdGNsXm8db0ERbUitvyhDFfJf6XOH_wctxXqguyNn_VkqFi4K61HfKFkzt4Qgh7Lp1zA=="
INFLUX_ORG   = "i"
INFLUX_BUCKET = "smartdose"

XLSX_PATH = "监测数据_25.xlsx"
MAX_ROWS  = 4380   # 前半年（8760/2）

def safe_float(val):
    try:
        return float(val) if val is not None else None
    except (TypeError, ValueError):
        return None

def main():
    print("读取 Excel...")
    wb = openpyxl.load_workbook(XLSX_PATH, read_only=True, data_only=True)
    ws = wb.active

    client = InfluxDBClient(url=INFLUX_URL, token=INFLUX_TOKEN, org=INFLUX_ORG)
    write_api = client.write_api(write_options=SYNCHRONOUS)

    batch = []
    count = 0

    for i, row in enumerate(ws.iter_rows(values_only=True)):
        if i == 0:  # 跳过表头
            continue
        if i > MAX_ROWS:
            break

        time_val = row[0]
        if time_val is None:
            continue

        # 转为 UTC 时间戳
        if hasattr(time_val, 'replace'):
            ts = time_val.replace(tzinfo=timezone.utc)
        else:
            continue

        # 出水数据
        out_point = (
            Point("water_quality")
            .tag("water_type", "out")
            .tag("is_predicted", "false")
            .time(ts)
        )
        fields = {
            "nh3n":    safe_float(row[14]),   # NH3_out
            "cod":     safe_float(row[15]),   # COD_out
            "tp":      safe_float(row[16]),   # TP_out
            "tn":      safe_float(row[17]),   # TN_out
            "ph":      safe_float(row[18]),   # PH_out
            "flow":    safe_float(row[13]),   # Flow_out
            "do_out":  safe_float(row[10]),   # DO_OD_out
            "temp":    safe_float(row[19]),   # T
        }
        for k, v in fields.items():
            if v is not None:
                out_point.field(k, v)
        batch.append(out_point)

        # 进水数据
        in_point = (
            Point("water_quality")
            .tag("water_type", "in")
            .tag("is_predicted", "false")
            .time(ts)
        )
        in_fields = {
            "cod":  safe_float(row[1]),   # COD_in
            "nh3n": safe_float(row[2]),   # NH3_in
            "tn":   safe_float(row[3]),   # TN_in
            "tp":   safe_float(row[4]),   # TP_in
        }
        for k, v in in_fields.items():
            if v is not None:
                in_point.field(k, v)
        batch.append(in_point)

        count += 1

        # 每 500 条批量写入一次
        if len(batch) >= 1000:
            write_api.write(bucket=INFLUX_BUCKET, org=INFLUX_ORG, record=batch)
            batch = []
            print(f"  已写入 {count} 条...")

    # 写入剩余数据
    if batch:
        write_api.write(bucket=INFLUX_BUCKET, org=INFLUX_ORG, record=batch)

    client.close()
    print(f"\n完成！共写入 {count} 条记录（进水+出水各 {count} 条）")

if __name__ == "__main__":
    main()
