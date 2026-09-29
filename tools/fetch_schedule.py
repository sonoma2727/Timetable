#!/usr/bin/env python3
"""Fetch course schedule from 强智科技教务系统 (jwxt.cqrk.edu.cn).

Usage:
  python fetch_schedule.py login-test
  python fetch_schedule.py fetch [--week N] [--out FILE]
  python fetch_schedule.py explore

Credentials: env TIMETABLE_ACCOUNT / TIMETABLE_PASSWORD, or tools/.cred lines:
  account=...
  password=...
"""
import argparse
import base64
import json
import os
import re
import sys
import urllib.error
import urllib.parse
import urllib.request
import http.cookiejar
from pathlib import Path

BASE = "http://jwxt.cqrk.edu.cn:18080"
LOGIN_URL = f"{BASE}/jsxsd/xk/LoginToXk"
KB_URL = f"{BASE}/jsxsd/xskb/xskb_list.do"

HERE = Path(__file__).resolve().parent
CRED_FILE = HERE / ".cred"
CACHE = HERE / ".cache"


# ---------------------------------------------------------------- credentials
def load_credentials():
    acct = os.environ.get("TIMETABLE_ACCOUNT")
    pwd = os.environ.get("TIMETABLE_PASSWORD")
    if acct and pwd:
        return acct, pwd
    if CRED_FILE.exists():
        data = {}
        for line in CRED_FILE.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                k, v = line.split("=", 1)
                data[k.strip()] = v.strip()
        if "account" in data and "password" in data:
            return data["account"], data["password"]
    sys.exit("missing credentials: set TIMETABLE_ACCOUNT/TIMETABLE_PASSWORD or create tools/.cred")


def encode_pwd(pwd):
    """Replicate xsMain.jsp submitForm1(): 。-> '.' (pwdstr1), ，-> ',' (pwdstr2)."""
    out = list(pwd)
    idx1, idx2 = [], []
    for i, ch in enumerate(pwd):
        if ch == "。":
            out[i] = "."
            idx1.append(str(i))
        elif ch == "，":
            out[i] = ","
            idx2.append(str(i))
    s1 = (",".join(idx1) + ",") if idx1 else ""
    s2 = (",".join(idx2) + ",") if idx2 else ""
    return "".join(out), s1, s2


def make_session():
    jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
    opener.addheaders = [("User-Agent",
                          "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                          "(KHTML, like Gecko) Chrome/126.0 Safari/537.36")]
    return opener


# ----------------------------------------------------------------------- login
def login(opener, account, password):
    pwd, s1, s2 = encode_pwd(password)
    encoded = (base64.b64encode(account.encode()).decode()
               + "%%%"
               + base64.b64encode(pwd.encode()).decode())
    body = urllib.parse.urlencode({
        "userAccount": account,
        "userPassword": "",
        "encoded": encoded,
        "pwdstr1": s1,
        "pwdstr2": s2,
    }).encode()
    req = urllib.request.Request(LOGIN_URL, data=body, method="POST")
    with opener.open(req, timeout=30) as resp:
        text = resp.read().decode("utf-8", "replace")
        final_url = resp.geturl()
    if "xsMain" not in final_url:
        err = extract_alert(text)
        raise RuntimeError(f"login failed (final={final_url}): {err}")
    return text


def extract_alert(html):
    m = re.search(r"alert\(['\"]([^'\"]+)['\"]\)", html)
    if m:
        return m.group(1)
    m = re.search(r'<font[^>]*color=["\']?red["\']?[^>]*>([^<]{2,80})</font>', html)
    return m.group(1).strip() if m else "unknown error"


# ----------------------------------------------------------------- fetch page
def fetch_kb(opener, week=None, save=False):
    params = {"Ves632DSdyV": "NEW_XSD_PYGL"}
    if week is not None:
        params["zc"] = str(week)
    url = KB_URL + "?" + urllib.parse.urlencode(params)
    with opener.open(url, timeout=30) as resp:
        raw = resp.read()
    try:
        html = raw.decode("utf-8")
    except UnicodeDecodeError:
        html = raw.decode("gb18030", "replace")
    if "请先登录" in html or ("<title>登录</title>" in html and "kbtable" not in html):
        raise RuntimeError("session expired: got login page instead of schedule")
    if save:
        CACHE.mkdir(exist_ok=True)
        name = f"xskb_w{week if week is not None else 'current'}.html"
        (CACHE / name).write_text(html, encoding="utf-8")
        print(f"[saved] {CACHE / name}", file=sys.stderr)
    return html


# ------------------------------------------------------------------ week info
def extract_week_info(html):
    """Pull semester / week selector / time-template info out of the page."""
    info = {}
    for name in ("xnxq01id", "zc", "kbjcmsid"):
        m = re.search(r'<select[^>]*name=["\']%s["\'][^>]*>(.*?)</select>' % name,
                      html, re.S | re.I)
        if not m:
            continue
        opts = re.findall(r'<option[^>]*value=["\']([^"\']*)["\']([^>]*)>(.*?)</option>',
                          m.group(1), re.S | re.I)
        if name == "zc":
            info["weeks"] = [int(v) for v, _, _ in opts if v.isdigit()]
        else:
            for v, attrs, text in opts:
                if "selected" in attrs:
                    info["term" if name == "xnxq01id" else "timeTemplate"] = strip_tags(text)
                    info[name] = v
    if "weeks" not in info:
        zs = sorted({int(z) for z in re.findall(r"[?&]zc=(\d+)", html)})
        if zs:
            info["weeks"] = zs
    info["selects"] = re.findall(r'<select[^>]*name=["\']([^"\']+)["\'][^>]*>', html)
    return info


# ---------------------------------------------------------------- table parse
CELL_RE = re.compile(r"<t[dh][^>]*>", re.I)
FONT_RE = re.compile(r"<font[^>]*title=['\"]([^'\"]*)['\"][^>]*>(.*?)</font>", re.S | re.I)
TAG_RE = re.compile(r"<[^>]+>")
SEP_RE = re.compile(r"-{5,}")


def strip_tags(s):
    s = re.sub(r"<br\s*/?>", "\n", s, flags=re.I)
    s = TAG_RE.sub("", s)
    s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
    return re.sub(r"[ \t]+", " ", s).strip()


def attrs_of(tag):
    return dict(re.findall(r'([\w:-]+)\s*=\s*["\']([^"\']*)["\']', tag))


WEEKVAL_RE = re.compile(r"([\d,\s、，\-]+)\(([周单双])\)")


def parse_weeks(value):
    """'1-12(周)[01-02节]' / '1,3,5(周)' -> explicit weeks list + periods."""
    weeks = []
    for m in WEEKVAL_RE.finditer(value):
        kind = m.group(2)
        body = m.group(1).replace("，", ",").replace("、", ",").replace(" ", "")
        for part in body.split(","):
            if not part:
                continue
            if "-" in part:
                a, b = part.split("-", 1)
                lo, hi = int(a), int(b)
            else:
                lo = hi = int(part)
            for w in range(lo, hi + 1):
                if kind == "单" and w % 2 == 0:
                    continue
                if kind == "双" and w % 2 == 1:
                    continue
                if w not in weeks:
                    weeks.append(w)
    weeks.sort()
    periods = []
    bracket = value.find("[")
    if bracket >= 0:
        periods = sorted({int(p) for p in re.findall(r"\d{1,2}", value[bracket:])})
    return weeks, periods


def parse_block(block_html):
    """One course entry inside a kbcontent div."""
    fonts = {t: strip_tags(v) for t, v in FONT_RE.findall(block_html)}
    name = " ".join(strip_tags(FONT_RE.split(block_html)[0]).split())
    name = name.strip("- ").strip()
    course = {"courseName": name}
    teacher = fonts.get("老师") or fonts.get("教师")
    if teacher:
        course["teacher"] = teacher
    if fonts.get("教室"):
        course["room"] = fonts["教室"]
    week_val = ""
    for t, v in fonts.items():
        if t.startswith("周次"):
            week_val = v
            break
    if week_val:
        course["weekText"] = week_val
        weeks, periods = parse_weeks(week_val)
        if weeks:
            course["weeks"] = weeks
        if periods:
            course["periods"] = periods
    if not name and not fonts:
        return None
    return course


def parse_table(html):
    m = re.search(r'<table[^>]*id=["\']kbtable["\'][^>]*>(.*?)</table>', html, re.S | re.I)
    if not m:
        raise RuntimeError("kbtable not found in page")
    table = m.group(1)
    trs = re.findall(r"<tr[^>]*>(.*?)</tr>", table, re.S | re.I)

    days = []
    grid = []          # list of rows; each row = {col: cell}
    times = []         # per-row {label, time}

    first_row_cells = re.findall(r"<th[^>]*>(.*?)</th>", trs[0], re.S | re.I)
    days = [strip_tags(c) for c in first_row_cells][1:]

    for r, tr in enumerate(trs[1:]):
        cells = re.findall(r"(<t[dh][^>]*>)(.*?)</t[dh]>", tr, re.S | re.I)
        if not cells:
            continue
        header_tag, header_html = cells[0]
        header_text = strip_tags(header_html)
        time_m = re.search(r"(\d{1,2}:\d{2}\s*-\s*\d{1,2}:\d{2})", header_text)
        period_label = header_text.replace(time_m.group(1), "").strip() if time_m else header_text
        time_range = time_m.group(1).replace(" ", "") if time_m else ""
        times.append({"label": period_label, "time": time_range})

        row_cells = {}
        for col, (tag, content) in enumerate(cells[1:]):
            a = attrs_of(tag)
            row_cells[col] = {
                "html": content,
                "rowspan": int(a.get("rowspan", 1)),
                "colspan": int(a.get("colspan", 1)),
            }
        grid.append(row_cells)

    # derive per-row 节次 numbering: row duration = n*45 + (n-1)*5 minutes
    row_first = {}
    period_to_row = {}
    next_period = 1
    for r, t in enumerate(times):
        if not t["time"] or "-" not in t["time"]:
            continue
        a, b = t["time"].split("-")
        dur = (int(b[:2]) * 60 + int(b[3:])) - (int(a[:2]) * 60 + int(a[3:]))
        n = (dur + 5) // 50
        if n < 1:
            n = 1
        row_first[r] = next_period
        for i in range(n):
            period_to_row[next_period + i] = r
        next_period += n

    def period_bounds(p):
        r = period_to_row[p]
        a, b = times[r]["time"].split("-")
        sh, sm = int(a[:2]), int(a[3:])
        base = sh * 60 + sm + (p - row_first[r]) * 50
        return base, base + 45

    def hhmm(m):
        return f"{m // 60:02d}:{m % 60:02d}"

    schedule = []
    seen = set()
    for r, row in enumerate(grid):
        if r < len(times) and "备注" in times[r]["label"]:
            continue
        for c, cell in sorted(row.items()):
            if c >= len(days) or not cell["html"].strip():
                continue
            for course in parse_cell(cell["html"]):
                periods = course.pop("periods", None)
                if not periods:
                    periods = [p for p in range(row_first.get(r, 1),
                                                row_first.get(r, 1) + 1)
                               if period_to_row.get(p) == r]
                    periods = periods or list(range(1, 2))
                rows = sorted({period_to_row[p] for p in periods if p in period_to_row})
                if rows:
                    start_row, end_row = rows[0], rows[-1]
                else:
                    start_row = end_row = r
                course["day"] = c + 1
                course["dayName"] = days[c] if c < len(days) else str(c + 1)
                course["row"] = start_row
                course["spanRows"] = end_row - start_row + 1
                course["periodLabel"] = times[start_row]["label"]
                course["periods"] = periods
                try:
                    starts = [period_bounds(p)[0] for p in periods]
                    ends = [period_bounds(p)[1] for p in periods]
                    course["timeStart"], course["timeEnd"] = hhmm(min(starts)), hhmm(max(ends))
                except KeyError:
                    course["timeStart"], course["timeEnd"] = split_time(times[r]["time"])
                key = (c + 1, course["courseName"], course.get("teacher", ""),
                       course.get("room", ""), course.get("weekText", ""), tuple(periods))
                if key in seen:
                    continue
                seen.add(key)
                schedule.append(course)
    schedule.sort(key=lambda x: (x["day"], x["row"], x["periods"][0] if x["periods"] else 0))
    return {"days": days, "times": times, "courses": schedule}


def split_time(t):
    if not t:
        return "", ""
    if "-" in t:
        a, b = t.split("-", 1)
        return a.strip(), b.strip()
    return "", ""


def parse_cell(td_html):
    """Return list of course dicts from one <td>."""
    courses = []
    # detailed variant first (has teacher); exact class match avoids sykb placeholders
    divs = re.findall(r'<div[^>]*class=["\']kbcontent["\'][^>]*>(.*?)</div>',
                      td_html, re.S | re.I)
    if not divs:
        divs = re.findall(r'<div[^>]*class=["\']kbcontent1["\'][^>]*>(.*?)</div>',
                          td_html, re.S | re.I)
    for div in divs:
        if "&nbsp;" in div and not FONT_RE.search(div):
            continue
        parts = SEP_RE.split(div)
        for part in parts:
            course = parse_block(part)
            if course and (course.get("courseName") or course.get("weekText")):
                courses.append(course)
    return courses


# ----------------------------------------------------------------------- main
def build_json(opener, week=None, save=False):
    html = fetch_kb(opener, week=week, save=save)
    meta = extract_week_info(html)
    parsed = parse_table(html)
    return {
        "source": BASE,
        "fetchedWeek": week,
        "weekInfo": meta,
        "days": parsed["days"],
        "times": parsed["times"],
        "courses": parsed["courses"],
    }


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd")
    sub.add_parser("login-test")
    f = sub.add_parser("fetch")
    f.add_argument("--week", type=int, default=None)
    f.add_argument("--out", default=None)
    f.add_argument("--save-html", action="store_true")
    sub.add_parser("explore")
    p = sub.add_parser("parse-html")
    p.add_argument("file")
    p.add_argument("--out", default=None)
    args = ap.parse_args()

    if args.cmd == "parse-html":
        html = Path(args.file).read_text(encoding="utf-8")
        data = {
            "weekInfo": extract_week_info(html),
            **parse_table(html),
        }
        text = json.dumps(data, ensure_ascii=False, indent=2)
        if args.out:
            Path(args.out).write_text(text, encoding="utf-8")
            print(f"wrote {args.out} ({len(data['courses'])} courses)")
        else:
            print(text)
        return

    account, password = load_credentials()
    opener = make_session()
    login(opener, account, password)

    if args.cmd == "login-test":
        print("login OK")
        return
    if args.cmd == "explore":
        html = fetch_kb(opener, save=True)
        print(json.dumps(extract_week_info(html), ensure_ascii=False, indent=2))
        m = re.search(r'<select[^>]*>.*?</select>', html, re.S)
        if m:
            print("--- first select ---")
            print(m.group(0)[:1200])
        for pat in (r"zc=\d+", r"xskb_list[^\"']*", r"当前周", r"学年学期"):
            found = sorted(set(re.findall(pat, html)))[:8]
            print(f"{pat}: {found}")
        return

    data = build_json(opener, week=args.week, save=args.save_html)
    text = json.dumps(data, ensure_ascii=False, indent=2)
    if args.out:
        Path(args.out).write_text(text, encoding="utf-8")
        print(f"wrote {args.out}")
    else:
        print(text)


if __name__ == "__main__":
    main()
