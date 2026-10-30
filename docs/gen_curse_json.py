#!/usr/bin/env python3
"""把 7 款诅咒的全部可读信息抽成 docs/curses.json（给人和 AI 查，替代翻两份 md）。

数据源（改完这些文件重跑本脚本即可，无需手改 JSON）：
  src/main/java/com/plumejade/lensouls/feather/CurseDefs.java    掩码布局 / 条目数 / blessing / noReverse
  src/main/java/com/plumejade/lensouls/feather/CurseGoals.java   反转条件目标值 + 单位
  src/main/resources/assets/lensouls/lang/{zh_cn,en_us}.json     文案（name/curse/after/cond）
  各 handler 的 CurseManager.on/rev/tick/reach/setProgress 调用  代码落点（文件:行）

用法（在 lensouls-template-1.21.1 下）：
  python tools/gen_curse_json.py
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAVA = ROOT / "src/main/java/com/plumejade/lensouls"
LANG = ROOT / "src/main/resources/assets/lensouls/lang"

UNIT_KEYS = {
    "BLOCKS": "blocks", "POINTS": "points", "KILLS": "kills", "TIMES": "times",
    "KINDS": "kinds", "CELLS": "cells", "DAYS": "days", "TIME": "time",
    "GENERIC": "generic", "NONE": "none",
}
NUMERALS = "①②③④⑤⑥⑦"

CALL_RE = re.compile(r"CurseManager\.(on|rev|tick|reach|setProgress|reverse)\(")
DEF_CONST_RE = re.compile(r"static final CurseDef (\w+) = CurseDefs\.(\w+);")
INT_CONST_RE = re.compile(r"static final int (\w+) = (\d+);")
DEF_DECL_RE = re.compile(
    r'CurseDef (\w+) = new CurseDef\("([^"]+)", (\d+), (\d+), (\w+), (\w+), (\d+), '
    r'Set\.of\(([^)]*)\), Set\.of\(([^)]*)\)\);'
)


def read(p):
    return p.read_text(encoding="utf-8")


def parse_defs():
    """CurseDefs.java → [{key, id, entries, offset, sectionAt, noReverse}]（保持声明顺序）"""
    src = read(JAVA / "feather/CurseDefs.java")
    out = []
    for m in DEF_DECL_RE.finditer(src):
        key, item_id, entries, offset, shared, grand, section_at, retained, no_reverse = m.groups()
        if key == "ALL":
            continue
        out.append({
            "key": key,
            "itemId": "lensouls:" + item_id,
            "entries": int(entries),
            "maskOffset": int(offset),
            "sharedCond": shared == "true",
            "grand": grand == "true",
            "sectionAt": int(section_at),
            "noReverse": [int(x) for x in re.findall(r"\d+", no_reverse)],
            "retained": [int(x) for x in re.findall(r"\d+", retained)],
        })
    return out


def parse_goals(defs):
    """CurseGoals.java → {(defKey, entryIndex): (goal, unitConst)}"""
    src = read(JAVA / "feather/CurseGoals.java")
    goals = {}
    loop_def = None
    for line in src.splitlines():
        m = re.search(r"for \(int (\w+) = 0; \w+ < CurseDefs\.(\w+)\.entries\(\)", line)
        if m:
            loop_def = m.group(2)
            continue
        m = re.search(r"put\(CurseDefs\.(\w+),\s*([^,]+),\s*(\d+)L,\s*(\w+)\)", line)
        if not m:
            continue
        key, entry, goal, unit = m.group(1), m.group(2).strip(), int(m.group(3)), m.group(4)
        d = next((x for x in defs if x["key"] == key), None)
        if d is None:
            continue
        if entry.isdigit():
            goals[(key, int(entry))] = (goal, unit)
        elif loop_def == key:
            for i in range(d["entries"]):
                goals[(key, i)] = (goal, unit)
    return goals


def split_args(text, start):
    """从 start（指向 '(' 之后）解析到配对 ')'，按顶层逗号切分；返回 (args, end_index)"""
    depth = 0
    args, cur = [], []
    for i in range(start, len(text)):
        ch = text[i]
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            if depth == 0:
                args.append("".join(cur).strip())
                return args, i
            depth -= 1
        elif ch == "," and depth == 0:
            args.append("".join(cur).strip())
            cur = []
            continue
        cur.append(ch)
    return args, len(text)


def scan_handlers(defs):
    """返回 refs[defKey][entryIndex] = ['path:line 方法(参数)', ...] 与 loopRefs / 目标值不一致清单"""
    by_const = {}          # 文件 → {javaConstName: defKey}
    refs = {d["key"]: {} for d in defs}
    loop_refs = {d["key"]: [] for d in defs}
    mismatches = []
    files = {}
    for f in sorted(list(JAVA.rglob("*.java"))):
        rel = f.relative_to(JAVA).as_posix()
        src = read(f)
        files[rel] = src

    for rel, src in files.items():
        consts = {m.group(1): m.group(2) for m in DEF_CONST_RE.finditer(src)}
        if not consts:
            continue
        ints = {m.group(1): int(m.group(2)) for m in INT_CONST_RE.finditer(src)
                if int(m.group(2)) < 128}
        lines = src.splitlines()
        for ln, line in enumerate(lines, 1):
            for m in CALL_RE.finditer(line):
                args, _ = split_args(line, m.end())
                if len(args) < 3:
                    continue
                def_key = consts.get(args[1])
                if def_key is None or def_key not in refs:
                    continue
                raw = args[2]
                entry = None
                if raw.isdigit():
                    entry = int(raw)
                elif raw in ints:
                    entry = ints[raw]
                label = f"{rel}:{ln} {m.group(1)}"
                if entry is None:
                    loop_refs[def_key].append(label + f"({raw})")
                    continue
                refs[def_key].setdefault(entry, []).append(label)
                # 一致性检查：tick(..., goal) 的 goal 实参（可解析时）必须与 CurseGoals 一致
                if m.group(1) == "tick" and len(args) >= 5:
                    g = args[4]
                    gv = int(g.rstrip("L")) if g.rstrip("L").isdigit() else ints.get(g)
                    if gv is not None:
                        mismatches.append((def_key, entry, gv, f"{rel}:{ln}"))
    return refs, loop_refs, mismatches


def main():
    defs = parse_defs()
    goals = parse_goals(defs)
    zh = json.loads(read(LANG / "zh_cn.json"))
    en = json.loads(read(LANG / "en_us.json"))
    refs, loop_refs, mismatches = scan_handlers(defs)

    gui = lambda u: zh.get("gui.lensouls.curse.prog." + u)

    feathers = []
    bad = []
    for n, d in enumerate(defs):
        key, iid = d["key"], d["itemId"]
        base = f"item.lensouls.{iid.split(':')[1]}"
        entries = []
        for i in range(d["entries"]):
            k = f"{base}.e{i + 1}"
            goal, unit = goals.get((key, i), (0, "NONE"))
            real_goal = goal if goal > 0 else 0
            if unit != "NONE" and real_goal != goal:
                bad.append((key, i))
            item = {
                "n": i + 1,
                "maskBit": d["maskOffset"] + i,
                "name": zh.get(k + ".name"),
                "curse": zh.get(k + ".curse"),
                "cond": zh.get(k + ".cond") or (zh.get(base + ".cond_shared") if d["sharedCond"] else None),
                "after": zh.get(k + ".after"),
                "merged": zh.get(k + ".merged"),
                "en": {f: en.get(k + "." + f) for f in ("name", "curse", "cond", "after", "merged") if en.get(k + "." + f)},
                "target": {"goal": real_goal, "unit": UNIT_KEYS.get(unit, unit), "unitText": gui(UNIT_KEYS.get(unit, unit))},
            }
            if d["sectionAt"] and i >= d["sectionAt"]:
                item["isBlessing"] = True
            if (i + 1) in d["noReverse"]:
                item["noReverse"] = True
            r = refs[key].get(i) or []
            if r:
                item["refs"] = r
            entries.append(item)
        feathers.append({
            "number": NUMERALS[n] if n < len(NUMERALS) else f"#{n + 1}",
            "key": key,
            "itemId": iid,
            "name": zh.get(base),
            "motto": zh.get(base + ".motto"),
            "desc": zh.get(base + ".desc"),
            "wear": zh.get(base + ".wear"),
            "progressText": zh.get(base + ".progress") or zh.get(base + ".progress_split"),
            "maskOffset": d["maskOffset"],
            "entries": entries,
            "sharedCond": zh.get(base + ".cond_shared") if d["sharedCond"] else None,
            "blessingsFrom": d["sectionAt"] or None,
            "noReverse": d["noReverse"] or None,
            "extraLang": {s: zh.get(base + "." + s) for s in ("grand",) if zh.get(base + "." + s)},
            "loopRefs": loop_refs[key] or None,
        })

    doc = {
        "_meta": {
            "what": "Len Souls 7 款羽系诅咒的全量数据：中文/英文文案 + 反转条件目标值 + 代码落点",
            "generatedBy": "python tools/gen_curse_json.py（不要手改本文件，改源码后重跑）",
            "sources": [
                "feather/CurseDefs.java（掩码布局/条目数/blessing/noReverse）",
                "feather/CurseGoals.java（目标值 + 单位）",
                "lang/zh_cn.json + lang/en_us.json（文案）",
                "各 handler 的 CurseManager.on/rev/tick/reach 调用（代码落点）",
            ],
            "maskLayout": "一个 long 共 64 位，位序 = maskOffset + 条目下标；① 0..2 ② 3..5 ③ 6..7 ④ 8..19 ⑤ 20..42 ⑥ 43..49 ⑦ 50..63",
            "stackingRule": "诅咒态乘算、反转态加算（设计文档 §2「叠加口径（定稿）」）；⑦ 第 8 条起是祝福不是诅咒",
            "state": "进度存玩家附件 CurseStateData（copyOnDeath）；查/改统一走 CurseManager.on/rev/tick/reach/resetProgress",
            "caveats": [
                "① ② ③ 三条/两条共用 def 级 cond_shared，逐条没有各自的条件文案",
                "④ e1「虚无的承诺」无反转条件（noReverse），progress 显示 /11",
                "⑦ 第 8~14 条是「祝福」（blessing），after 字段是解锁后的增益",
                "refs 是调用点快照（文件:行），源码增删行后行号会漂移——以本脚本重跑结果为准",
                "target.goal 与 handler 里 tick 的目标实参应当一致，脚本会把两边都抓下来，不一致见 _meta.targetMismatches",
            ],
            "targetMismatches": [
                {"def": k, "n": i + 1, "handlerGoal": gv, "site": site}
                for (k, i, gv, site) in mismatches
                if goals.get((k, i), (0, ""))[0] != gv
            ],
        },
        "totals": {
            "feathers": len(feathers),
            "curses": sum(1 for d in defs for i in range(d["sectionAt"] or d["entries"])),
            "blessings": sum(d["entries"] - d["sectionAt"] for d in defs if d["sectionAt"]),
            "entries": sum(d["entries"] for d in defs),
        },
        "feathers": feathers,
    }

    out = ROOT / "docs/curses.json"
    out.write_text(json.dumps(doc, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"wrote {out.relative_to(ROOT)}  ({out.stat().st_size} bytes)")
    print(f"feathers={doc['totals']['feathers']} entries={doc['totals']['entries']} "
          f"curses={doc['totals']['curses']} blessings={doc['totals']['blessings']}")
    miss_refs = [(f["key"], e["n"]) for f in feathers for e in f["entries"] if "refs" not in e]
    if miss_refs:
        print("无 refs 的条目:", miss_refs)
    if doc["_meta"]["targetMismatches"]:
        print("目标值不一致:", doc["_meta"]["targetMismatches"])


if __name__ == "__main__":
    main()
