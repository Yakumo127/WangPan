#!/usr/bin/env python3
"""将 aa.txt 中的 JSON 数组转换为中文表头的 CSV。"""

import csv
import json
import sys
from pathlib import Path


def read_json_array(input_path: Path):
    """读取并校验 JSON 数组格式。"""
    if not input_path.exists():
        raise FileNotFoundError("未找到 aa.txt，请将文件放在脚本同目录后重试。")

    try:
        content = input_path.read_text(encoding="utf-8")
    except OSError as exc:
        raise RuntimeError(f"读取 aa.txt 失败: {exc}") from exc

    try:
        data = json.loads(content)
    except json.JSONDecodeError as exc:
        raise ValueError(f"aa.txt 不是合法的 JSON: {exc}") from exc

    if not isinstance(data, list):
        raise ValueError("aa.txt 内容不是 JSON 数组，请检查格式。")

    return data


def visibility_label(value):
    """将公开性数字转换为中文标签。"""
    try:
        level = int(value)
    except (TypeError, ValueError):
        return "未知"

    if level == 0:
        return "私有"
    if level == 1:
        return "公开"
    return f"未知({level})"


def write_csv(records, output_path: Path):
    """写入 CSV，返回总数、写入数、跳过数。"""
    headers = [
        ("lfs_repository_size", "LFS存储空间"),
        ("lfs_storage_ratio", "LFS存储占比"),
        ("name_with_namespace", "仓库名称"),
        ("path_with_namespace", "详细网址"),
        ("repository_size", "Git存储空间"),
        ("storage_ratio", "Git存储占比"),
        ("visibility_level", "公开性"),
    ]

    written = 0
    skipped = 0

    with output_path.open("w", newline="", encoding="utf-8-sig") as csvfile:
        writer = csv.writer(csvfile)
        writer.writerow([label for _, label in headers])

        for item in records:
            if not isinstance(item, dict):
                skipped += 1
                continue

            row = [
                item.get("lfs_repository_size", ""),
                item.get("lfs_storage_ratio", ""),
                item.get("name_with_namespace", ""),
                item.get("path_with_namespace", ""),
                item.get("repository_size", ""),
                item.get("storage_ratio", ""),
                visibility_label(item.get("visibility_level")),
            ]
            writer.writerow(row)
            written += 1

    return len(records), written, skipped


def main():
    base_dir = Path(__file__).resolve().parent
    input_path = base_dir / "aa.txt"
    output_path = base_dir / "aa.csv"

    try:
        records = read_json_array(input_path)
    except Exception as exc:
        sys.stderr.write(f"{exc}\n")
        sys.exit(1)

    total, written, skipped = write_csv(records, output_path)
    print(
        f"转换完成，输出文件：{output_path.name}。总计 {total} 条，"
        f"写入 {written} 条，跳过 {skipped} 条。"
    )


if __name__ == "__main__":
    main()
