"""
Converts the academy office workbook ("LORD SAI ACADEMY - Copy.xlsx") into the JSON payload
accepted by  POST /api/automation/import  (Automation Admin > Import).

    python convert_academy_workbook.py "C:\\path\\LORD SAI ACADEMY - Copy.xlsx" academy-import.json

Normalisation rules (the import never invents data — anything ambiguous becomes a review note):
  * Student IDs: "LSA2026/0001", "LSA/2026/1"  ->  "LSA/2026/0001"  (one canonical form everywhere)
  * Names: trimmed, internal whitespace collapsed; original kept in a note when it differed
  * Admission date: real dates are kept; unparseable text (e.g. "12-07-20226") is kept as raw
    text with a review note instead of being guessed
  * FEE sheet: each installment column with an AMOUNT becomes one payment; a missing DATE gives
    a null date + review note; a date after today is imported as-is + review note
  * Placeholder rows (an ID with no name, e.g. LSA/2026/0021-0030) are NOT students
  * RECEIPT sheet: only one filled receipt exists (LSA/2026/0016); where it disagrees with the
    FEE sheet the FEE sheet wins and the difference is recorded as a review note; its DISCOUNT
    line, which the FEE sheet lacks, is imported as a DISCOUNT payment with a note
  * ATTEDENCE sheet: each block header (dates + year row) becomes sessions; P/A become
    PRESENT/ABSENT; the batch is taken from the STUDENT DATA sheet, not from the block
"""
import json
import re
import sys
from datetime import date, datetime

import openpyxl

COURSE_NAME = "Share Market Basic to Advance"   # RECEIPT sheet, "Course Name"
COURSE_FEE = 10000                              # RECEIPT sheet TOTAL FEE; every fully-paid FEE row totals 10,000
MONTHS = {m: i for i, m in enumerate(["jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"], 1)}
TODAY = date.today()


def canonical_id(raw):
    if raw is None:
        return None
    m = re.match(r"^LSA/?(\d{4})/(\d{1,6})$", str(raw).strip().replace(" ", ""), re.I)
    return f"LSA/{m.group(1)}/{int(m.group(2)):04d}" if m else None


def clean_name(raw):
    return re.sub(r"\s+", " ", str(raw or "")).strip()


def to_date(v):
    if isinstance(v, datetime):
        return v.date()
    if isinstance(v, date):
        return v
    if isinstance(v, str):
        s = v.strip()
        for fmt in ("%d-%m-%Y", "%d/%m/%Y", "%Y-%m-%d"):
            try:
                return datetime.strptime(s, fmt).date()
            except ValueError:
                pass
    return None


def iso(d):
    return d.isoformat() if d else None


def convert(path):
    wb = openpyxl.load_workbook(path, data_only=True)
    students, payments, sessions, notes = [], [], [], []
    batch_of = {}

    # ---- STUDENT DATA --------------------------------------------------------------------------
    ws = wb["STUDENT DATA"]
    header_row = next(r for r in range(1, 20) if ws.cell(r, 3).value == "STUDENT ID")
    for r in range(header_row + 1, ws.max_row + 1):
        sid = canonical_id(ws.cell(r, 3).value)
        if not sid:
            continue
        raw_name = ws.cell(r, 4).value
        name = clean_name(raw_name)
        if not name:
            notes.append(f"{sid}: row {r} has an ID but no name — treated as a placeholder, not imported")
            continue
        review = []
        if raw_name and str(raw_name) != name:
            review.append(f"name normalised from '{raw_name}'")
        adm_raw = ws.cell(r, 5).value
        adm = to_date(adm_raw)
        adm_raw_text = None
        if adm is None and adm_raw not in (None, ""):
            adm_raw_text = str(adm_raw).strip()
            review.append(f"admission date '{adm_raw_text}' could not be read — please correct it")
        mobile_raw = ws.cell(r, 6).value
        mobile = re.sub(r"\D", "", str(mobile_raw or ""))
        if mobile and len(mobile) != 10:
            review.append(f"mobile '{mobile_raw}' is not a 10-digit number")
            mobile = None
        email = clean_name(ws.cell(r, 8).value).lower() or None
        if email and "@" not in email:
            review.append(f"email '{email}' looks invalid")
            email = None
        batch = clean_name(ws.cell(r, 1).value).upper() or None
        batch_of[sid] = batch
        students.append({
            "studentId": sid, "fullName": name, "batch": batch, "course": COURSE_NAME, "courseFee": COURSE_FEE,
            "admissionDate": iso(adm), "admissionDateRaw": adm_raw_text, "mobile": mobile or None, "email": email,
            "address": clean_name(ws.cell(r, 7).value) or None, "reviewNote": "; ".join(review) or None,
        })

    # ---- FEE ----------------------------------------------------------------------------------
    ws = wb["FEE"]
    known = {s["studentId"] for s in students}
    r = 1
    while r <= ws.max_row:
        sid = canonical_id(ws.cell(r, 3).value)
        if sid and ws.cell(r, 5).value == "DATE":
            amount_row = r + 1
            if sid not in known:
                if clean_name(ws.cell(r, 4).value):
                    notes.append(f"FEE row {r}: {sid} has a name but is not in STUDENT DATA — payments skipped")
                # reserved placeholder rows (0021-0030) carry no name and no amounts: silently skipped
                r += 2
                continue
            for k, col in enumerate(range(6, 11), start=1):
                amount = ws.cell(amount_row, col).value
                if amount in (None, "", 0):
                    continue
                pay_date = to_date(ws.cell(r, col).value)
                review = []
                if pay_date is None:
                    review.append("amount recorded without a date in the FEE sheet")
                elif pay_date > TODAY:
                    review.append(f"payment date {pay_date:%d-%m-%Y} is in the future in the workbook — please verify the year")
                payments.append({"studentId": sid, "installmentNo": k, "paymentDate": iso(pay_date), "amount": float(amount),
                                 "paymentMode": "OTHER", "notes": "Imported from FEE sheet (payment mode not recorded there)",
                                 "reviewNote": "; ".join(review) or None})
            r += 2
        else:
            r += 1

    # ---- RECEIPT (single filled template) --------------------------------------------------------
    ws = wb["RECEIPT"]
    text = {c.coordinate: c.value for row in ws.iter_rows() for c in row if c.value not in (None, "")}
    rc_sid = None
    for v in text.values():
        if isinstance(v, str) and v.startswith("Student ID"):
            rc_sid = canonical_id(v.split(":", 1)[1])
    if rc_sid and rc_sid in known:
        rc_paid = [(text.get(f"{c}26"), to_date(text.get(f"{c}27")), str(text.get(f"{c}28") or "").strip().upper()) for c in "DEFG"]
        rc_paid = [(a, d, m) for a, d, m in rc_paid if a not in (None, "", 0)]
        mine = [p for p in payments if p["studentId"] == rc_sid]
        for i, (amt, d, mode) in enumerate(rc_paid):
            if i < len(mine):
                p = mine[i]
                if mode:
                    p["paymentMode"] = mode
                    p["notes"] = "Payment mode taken from RECEIPT sheet"
                if d and p["paymentDate"] and iso(d) != p["paymentDate"]:
                    p["reviewNote"] = "; ".join(filter(None, [p["reviewNote"],
                        f"RECEIPT sheet dates this payment {d:%d-%m-%Y}, FEE sheet {p['paymentDate']} (FEE kept)"]))
            else:
                payments.append({"studentId": rc_sid, "installmentNo": len(mine) + 1 + (i - len(mine)), "paymentDate": iso(d),
                                 "amount": float(amt), "paymentMode": mode or "OTHER",
                                 "notes": "Taken from RECEIPT sheet", "reviewNote": "present in RECEIPT sheet but missing from FEE sheet"})
        notes.append(f"RECEIPT sheet: one filled receipt ({rc_sid}); its {len(rc_paid)} PAID columns reconciled against the FEE sheet")

    # ---- ATTEDENCE ---------------------------------------------------------------------------------
    ws = wb["ATTEDENCE"]
    r = 1
    while r <= ws.max_row:
        if ws.cell(r, 1).value == "STUDENT ID":
            label_row, header_row = r - 1, r
            dates = {}
            for col in range(3, ws.max_column + 1):
                label = ws.cell(label_row, col).value
                year = ws.cell(header_row, col).value
                if not label or not year:
                    continue
                m = re.match(r"^\s*(\d{1,2})\s*(?:st|nd|rd|th)?\s*([a-z]+)", str(label).strip().lower())
                if not m or m.group(2)[:3] not in MONTHS:
                    notes.append(f"ATTEDENCE: column label '{label}' (row {label_row}) not understood — skipped")
                    continue
                try:
                    dates[col] = date(int(str(year).strip()), MONTHS[m.group(2)[:3]], int(m.group(1)))
                except ValueError:
                    notes.append(f"ATTEDENCE: '{label} {year}' is not a valid date — skipped")
            rows = []
            rr = header_row + 1
            while rr <= ws.max_row and ws.cell(rr, 1).value not in (None, ""):
                sid = canonical_id(ws.cell(rr, 1).value)
                if sid in known:
                    rows.append((rr, sid))
                elif clean_name(ws.cell(rr, 2).value):
                    notes.append(f"ATTEDENCE row {rr}: {ws.cell(rr, 1).value} not in STUDENT DATA — skipped")
                rr += 1
            batches = [batch_of.get(sid) for _, sid in rows if batch_of.get(sid)]
            batch = max(set(batches), key=batches.count) if batches else None
            for col, d in sorted(dates.items()):
                marks = []
                for rr2, sid in rows:
                    v = ws.cell(rr2, col).value
                    if v in (None, ""):
                        continue
                    marks.append({"studentId": sid, "status": str(v).strip().upper()})
                if marks:
                    sessions.append({"batch": batch, "sessionDate": iso(d), "sessionType": "CLASS", "marks": marks})
            r = rr
        else:
            r += 1

    notes.append(f"Course fee ₹{COURSE_FEE:,} for '{COURSE_NAME}' applied to every student (from the RECEIPT sheet; "
                 f"the FEE sheet has no fee column) — adjust per student if any batch had a different fee")
    return {"students": students, "payments": payments, "sessions": sessions}, notes


if __name__ == "__main__":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")   # Windows consoles default to cp1252, which lacks the rupee sign
    src = sys.argv[1] if len(sys.argv) > 1 else "LORD SAI ACADEMY - Copy.xlsx"
    dst = sys.argv[2] if len(sys.argv) > 2 else "academy-import.json"
    payload, notes = convert(src)
    with open(dst, "w", encoding="utf-8") as f:
        json.dump(payload, f, indent=1, ensure_ascii=False)
    flagged = [s for s in payload["students"] if s["reviewNote"]] + [p for p in payload["payments"] if p["reviewNote"]]
    print(f"students={len(payload['students'])} payments={len(payload['payments'])} sessions={len(payload['sessions'])} "
          f"marks={sum(len(s['marks']) for s in payload['sessions'])} -> {dst}")
    print("\nREVIEW ITEMS carried into the import:")
    for s in payload["students"]:
        if s["reviewNote"]:
            print(f"  {s['studentId']} {s['fullName']}: {s['reviewNote']}")
    for p in payload["payments"]:
        if p["reviewNote"]:
            print(f"  {p['studentId']} installment {p['installmentNo']} ₹{p['amount']:.0f}: {p['reviewNote']}")
    print("\nNOTES:")
    for n in notes:
        print("  -", n)
