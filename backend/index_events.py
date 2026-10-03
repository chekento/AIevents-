#!/usr/bin/env python3
import json, os, re, sys, time, hashlib
from datetime import datetime, timezone
from urllib.parse import quote_plus, urlparse, parse_qs, unquote
from pathlib import Path

import requests
from bs4 import BeautifulSoup
from dateutil import parser as dtparser
from ftfy import fix_text
from direct_hubs import collect as collect_direct_hubs

ROOT = Path(__file__).resolve().parents[1]
REGIONS_PATH = ROOT / "backend" / "regions.json"
INDEX_PATH = ROOT / "data" / "events-index.json"
UA = "AIevents-indexer/0.3 (+https://github.com/chekento/AIevents-)"
BROWSER_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/140 Safari/537.36"
TIMEOUT = 10

SOURCE_DOMAINS = [
    "globalai.community", "aitinkerers.org", "mlops.community", "meetup.com",
    "luma.com", "eventbrite.com", "sessionize.com", "pretalx.com",
    "gdg.community.dev", "reactor.microsoft.com", "aws.amazon.com",
    "nvidia.com", "huggingface.co", "ieee.org", "acm.org", "10times.com",
    "confs.tech", "dev.events", "eventyay.com"
]

EVENT_HINTS = (
    "event", "events", "meetup", "conference", "summit", "workshop",
    "hackathon", "calendar", "community", "stammtisch"
)

session = requests.Session()
session.headers.update({"User-Agent": UA, "Accept-Language": "en,de;q=0.9,*;q=0.5"})


def load_json(path, default):
    try:
        return json.loads(path.read_text("utf-8"))
    except Exception:
        return default


def save_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, sort_keys=False) + "\n", "utf-8")


def normalize_ddg(href):
    if not href:
        return None
    if href.startswith("//"):
        href = "https:" + href
    if href.startswith("/"):
        href = "https://duckduckgo.com" + href
    if "duckduckgo.com/l/" not in href:
        return href if href.startswith("http") else None
    try:
        qs = parse_qs(urlparse(href).query)
        return unquote(qs.get("uddg", [""])[0]) or None
    except Exception:
        return None


def likely_event_url(url):
    u = url.lower()
    return any(h in u for h in EVENT_HINTS) or any(d in u for d in SOURCE_DOMAINS)


def _collect_links(soup, selector, normalizer=lambda x: x, limit=10):
    out = []
    for a in soup.select(selector):
        u = normalizer(a.get("href", ""))
        if u and u.startswith("https://") and likely_event_url(u) and u not in out:
            out.append(u)
        if len(out) >= limit:
            break
    return out


def discover(query, limit=10):
    errors = []

    # DuckDuckGo first; some cloud runner IPs are rate-limited, so never rely on it alone.
    try:
        url = "https://html.duckduckgo.com/html/?q=" + quote_plus(query)
        r = session.get(url, timeout=TIMEOUT, headers={"User-Agent": BROWSER_UA})
        r.raise_for_status()
        links = _collect_links(
            BeautifulSoup(r.text, "html.parser"),
            "a.result__a, a[data-testid='result-title-a']",
            normalize_ddg,
            limit
        )
        if links:
            return links
    except Exception as exc:
        errors.append("ddg=" + str(exc))

    # Bing HTML is a practical no-key fallback for scheduled public-web discovery.
    try:
        url = "https://www.bing.com/search?q=" + quote_plus(query) + "&count=" + str(max(10, limit))
        r = session.get(url, timeout=TIMEOUT, headers={"User-Agent": BROWSER_UA})
        r.raise_for_status()
        links = _collect_links(
            BeautifulSoup(r.text, "html.parser"),
            "li.b_algo h2 a, a.tilk",
            lambda x: x,
            limit
        )
        if links:
            return links
    except Exception as exc:
        errors.append("bing=" + str(exc))

    # Last-resort Google HTML fallback. CAPTCHA/consent pages simply yield no results.
    try:
        url = "https://www.google.com/search?q=" + quote_plus(query) + "&num=" + str(max(10, limit))
        r = session.get(url, timeout=TIMEOUT, headers={"User-Agent": BROWSER_UA})
        r.raise_for_status()
        links = _collect_links(
            BeautifulSoup(r.text, "html.parser"),
            "div.yuRUbf a, a[jsname='UWckNb']",
            lambda x: x,
            limit
        )
        if links:
            return links
    except Exception as exc:
        errors.append("google=" + str(exc))

    if errors:
        raise RuntimeError("; ".join(errors))
    return []


def iter_nodes(node):
    if isinstance(node, dict):
        yield node
        for value in node.values():
            if isinstance(value, (dict, list)):
                yield from iter_nodes(value)
    elif isinstance(node, list):
        for item in node:
            yield from iter_nodes(item)


def types_of(obj):
    t = obj.get("@type", "")
    if isinstance(t, list):
        return [str(x) for x in t]
    return [str(t)]


def parse_dt(value):
    if not value:
        return None
    try:
        dt = dtparser.parse(str(value))
        if dt.tzinfo is None:
            dt = dt.replace(tzinfo=timezone.utc)
        return dt
    except Exception:
        return None


def current_or_future(start, end):
    # Central index is intentionally strict: no undated or already-ended events.
    if start is None:
        return False
    now = datetime.now(timezone.utc)
    if end is not None:
        return end > now
    return start >= now


def repair_text(value):
    if not isinstance(value, str):
        return value
    decoded = unquote(value) if "%" in value else value
    return fix_text(decoded)


def clean_text(value):
    if not value:
        return ""
    return re.sub(r"\s+", " ", BeautifulSoup(str(value), "html.parser").get_text(" ", strip=True)).strip()


def address_text(address):
    if isinstance(address, str):
        return address.strip()
    if not isinstance(address, dict):
        return ""
    parts = [
        address.get("streetAddress"), address.get("postalCode"),
        address.get("addressLocality"), address.get("addressRegion"),
        address.get("addressCountry")
    ]
    return ", ".join(str(x).strip() for x in parts if x and str(x).strip())


def first_offer(offers):
    if isinstance(offers, list):
        return offers[0] if offers else {}
    return offers if isinstance(offers, dict) else {}


def image_url(value):
    if isinstance(value, str):
        return value if value.startswith("http") else ""
    if isinstance(value, dict):
        candidate = value.get("url") or value.get("contentUrl") or ""
        return str(candidate) if str(candidate).startswith("http") else ""
    if isinstance(value, list) and value:
        return image_url(value[0])
    return ""


def price_text(offers):
    o = first_offer(offers)
    p = str(o.get("price", "")).strip()
    c = str(o.get("priceCurrency", "")).strip()
    if not p:
        return ""
    if p in {"0", "0.0", "0.00"}:
        return "Free"
    return (p + " " + c).strip()


def parse_geo(location):
    if not isinstance(location, dict):
        return None
    geo = location.get("geo")
    if not isinstance(geo, dict):
        return None
    try:
        lat = float(geo.get("latitude"))
        lon = float(geo.get("longitude"))
        if -90 <= lat <= 90 and -180 <= lon <= 180:
            return {"lat": lat, "lon": lon}
    except Exception:
        pass
    return None


def organizer_name(value):
    if isinstance(value, str):
        return value.strip()
    if isinstance(value, dict):
        return str(value.get("name", "")).strip()
    if isinstance(value, list):
        names = [organizer_name(x) for x in value]
        return ", ".join(x for x in names if x)
    return ""


def event_key(e):
    event_url = str(e.get("eventUrl") or "").strip()
    if event_url.startswith("http"):
        parsed = urlparse(event_url)
        host = (parsed.hostname or "").lower().removeprefix("www.")
        path = parsed.path.rstrip("/").lower()
        if host == "forum.openai.com":
            path = path.replace("/home/events/", "/public/events/")
        canonical = host + path
        if canonical:
            return hashlib.sha256(("url|" + canonical).encode()).hexdigest()[:24]
    start = e.get("start") or ""
    title = re.sub(r"\s+", " ", e.get("title", "").lower()).strip()
    place = re.sub(r"\s+", " ", e.get("locality", "").lower()).strip()
    return hashlib.sha256((title + "|" + start + "|" + place).encode()).hexdigest()[:24]


def parse_html_event_fallback(soup, url, region):
    title_node = soup.find("h1")
    title = clean_text(title_node.get_text(" ", strip=True) if title_node else "")
    if len(title) < 3:
        return []

    full_text = clean_text(soup.get_text(" ", strip=True))
    if re.search(r"\b(cancelled|canceled)\b", full_text[:800], re.I):
        return []

    month = r"(January|February|March|April|May|June|July|August|September|October|November|December|Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)"
    pattern = re.compile(
        r"(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun)?\w*,?\s*"
        r"(\d{1,2})\s+" + month + r"\s+(\d{4})\s*[·•]\s*"
        r"(\d{1,2}:\d{2})(?:\s*[-–]\s*(\d{1,2}:\d{2}))?"
        r"(?:.{0,100}?UTC\s*([+-]\d{2}:\d{2}))?",
        re.I
    )
    match = pattern.search(full_text)
    if not match:
        return []

    day, month_name, year, start_clock, end_clock, offset = match.groups()
    offset = offset or "+00:00"
    start = parse_dt(f"{day} {month_name} {year} {start_clock} {offset}")
    end = parse_dt(f"{day} {month_name} {year} {end_clock} {offset}") if end_clock else None
    if not current_or_future(start, end):
        return []

    locality = ""
    lines = [clean_text(x) for x in soup.get_text("\n", strip=True).splitlines()]
    date_idx = next((i for i, line in enumerate(lines) if pattern.search(line)), -1)
    if date_idx >= 0:
        for line in lines[date_idx + 1:date_idx + 7]:
            lower = line.lower()
            if not line or lower.startswith(("this event", "register", "about", "schedule", "agenda")):
                continue
            if "online" in lower or "," in line:
                locality = line[:300]
                break

    organizer = ""
    om = re.search(r"This event is organized by\s+(.+?)(?:\s+\.|\s+Register|\s+About)", full_text, re.I)
    if om:
        organizer = clean_text(om.group(1))[:160]

    description = ""
    meta = soup.find("meta", attrs={"name": "description"})
    if meta and meta.get("content"):
        description = clean_text(meta.get("content"))
    if not description:
        for para in soup.find_all("p"):
            text = clean_text(para.get_text(" ", strip=True))
            if len(text) >= 50:
                description = text
                break

    online = "online" in locality.lower() or bool(re.search(r"\bOnline\b", full_text[:1200]))
    og = soup.find("meta", attrs={"property": "og:image"})
    image = str(og.get("content", "")) if og and og.get("content") else ""
    source_name = (urlparse(url).hostname or "web").removeprefix("www.")
    event = {
        "title": title,
        "start": start.astimezone(timezone.utc).isoformat().replace("+00:00", "Z"),
        "end": end.astimezone(timezone.utc).isoformat().replace("+00:00", "Z") if end else None,
        "venue": "",
        "locality": locality,
        "description": description[:1200],
        "organizer": organizer,
        "sourceName": source_name,
        "sourceUrl": url,
        "eventUrl": url,
        "price": "",
        "language": "",
        "online": online,
        "geo": None,
        "confidence": 75 if locality else 68,
        "imageUrl": image,
        "regions": [region],
        "indexedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
    }
    event["key"] = event_key(event)
    return [event]


def parse_page(url, region):
    r = session.get(url, timeout=TIMEOUT)
    if r.status_code >= 400:
        return []
    soup = BeautifulSoup(r.content, "html.parser")
    events = []
    for script in soup.select("script[type='application/ld+json']"):
        raw = script.string or script.get_text()
        if not raw:
            continue
        try:
            data = json.loads(raw)
        except Exception:
            continue
        for obj in iter_nodes(data):
            if not any(t.lower().endswith("event") for t in types_of(obj)):
                continue
            status = str(obj.get("eventStatus", ""))
            if "EventCancelled" in status or "EventCanceled" in status:
                continue
            title = str(obj.get("name", "")).strip()
            if len(title) < 3:
                continue
            start = parse_dt(obj.get("startDate"))
            end = parse_dt(obj.get("endDate"))
            if not current_or_future(start, end):
                continue
            loc = obj.get("location")
            venue = str(loc.get("name", "")).strip() if isinstance(loc, dict) else (str(loc).strip() if isinstance(loc, str) else "")
            address = address_text(loc.get("address")) if isinstance(loc, dict) else ""
            locality = ", ".join(dict.fromkeys(x for x in [venue, address] if x))
            mode = str(obj.get("eventAttendanceMode", ""))
            online = "Online" in mode or "virtual" in str(loc).lower() or "online" in locality.lower()
            source_name = (urlparse(url).hostname or "web").removeprefix("www.")
            event_url = str(obj.get("url", "")).strip()
            if not event_url.startswith("http"):
                event_url = url
            confidence = 55 + 20
            if locality or online: confidence += 10
            org = organizer_name(obj.get("organizer"))
            if org: confidence += 5
            geo = parse_geo(loc)
            if geo: confidence += 5
            price = price_text(obj.get("offers"))
            if price: confidence += 5
            image = image_url(obj.get("image"))
            if not image:
                og = soup.find("meta", attrs={"property": "og:image"})
                image = str(og.get("content", "")) if og and og.get("content") else ""
            e = {
                "title": title,
                "start": start.astimezone(timezone.utc).isoformat().replace("+00:00", "Z") if start else None,
                "end": end.astimezone(timezone.utc).isoformat().replace("+00:00", "Z") if end else None,
                "venue": venue,
                "locality": locality,
                "description": clean_text(obj.get("description", ""))[:1200],
                "organizer": org,
                "sourceName": source_name,
                "sourceUrl": url,
                "eventUrl": event_url,
                "price": price,
                "language": str(obj.get("inLanguage", "")),
                "online": bool(online),
                "geo": geo,
                "confidence": min(confidence, 100),
                "imageUrl": image,
                "regions": [region],
                "indexedAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z")
            }
            e["key"] = event_key(e)
            events.append(e)
    if not events:
        events.extend(parse_html_event_fallback(soup, url, region))
    return events


def merge_events(existing, incoming):
    merged = {}
    for e in existing + incoming:
        for field in ("title", "venue", "locality", "description", "organizer", "sourceName", "price", "language"):
            if field in e:
                e[field] = repair_text(e.get(field))
        start = parse_dt(e.get("start"))
        end = parse_dt(e.get("end"))
        if not current_or_future(start, end):
            continue
        key = event_key(e)
        e["key"] = key
        if key not in merged:
            merged[key] = e
        else:
            old = merged[key]
            old_regions = set(old.get("regions", []))
            old_regions.update(e.get("regions", []))
            # Prefer the richer record.
            old_score = old.get("confidence", 0) + len(old.get("description", "")) / 100
            new_score = e.get("confidence", 0) + len(e.get("description", "")) / 100
            if new_score > old_score:
                e["regions"] = sorted(old_regions)
                merged[key] = e
            else:
                old["regions"] = sorted(old_regions)
    return sorted(
        merged.values(),
        key=lambda e: (e.get("start") is None, e.get("start") or "9999", e.get("title", "").lower())
    )


def main():
    config = load_json(REGIONS_PATH, {"regions": [], "batch_size": 8})
    state = load_json(INDEX_PATH, {"cursor": 0, "events": []})
    regions = config.get("regions", [])
    if not regions:
        print("No regions configured")
        return 1

    batch_size = max(1, min(int(config.get("batch_size", 8)), 20))
    cursor = int(state.get("cursor", 0)) % len(regions)
    rotating_batch = [regions[(cursor + i) % len(regions)] for i in range(min(batch_size, len(regions)))]
    priority_regions = config.get("priority_regions", [])
    batch = list(dict.fromkeys(priority_regions + rotating_batch))

    incoming = []
    incoming.extend(collect_direct_hubs(parse_page))
    print("Direct hub discovered records:", len(incoming))

    for idx, region in enumerate(batch):
        print(f"[{idx+1}/{len(batch)}] {region}")
        queries = [
            f'"{region}" AI artificial intelligence event meetup conference workshop',
            f'"{region}" AI agents LLM RAG MCP meetup',
            f'"{region}" AI community user group Stammtisch'
        ]
        # Rotate a small source subset to keep each run bounded.
        source_offset = (cursor + idx) % len(SOURCE_DOMAINS)
        for d in [SOURCE_DOMAINS[source_offset], SOURCE_DOMAINS[(source_offset + 7) % len(SOURCE_DOMAINS)]]:
            queries.append(f'site:{d} "{region}" AI event')
        if region in priority_regions:
            queries.extend([
                f'site:meetup.com "{region}" AI meetup',
                f'site:lu.ma "{region}" AI LLM agents event',
                f'site:eventbrite.com "{region}" artificial intelligence event'
            ])

        links = []
        for q in queries:
            try:
                links.extend(discover(q, limit=8))
            except Exception as exc:
                print(" discovery failed:", exc)
            time.sleep(0.6)
        links = list(dict.fromkeys(links))[:24]

        for url in links:
            try:
                incoming.extend(parse_page(url, region))
            except Exception as exc:
                print(" page failed:", url, exc)
            time.sleep(0.25)

    merged = merge_events(state.get("events", []), incoming)
    next_cursor = (cursor + len(rotating_batch)) % len(regions)
    output = {
        "schema": 1,
        "generated_at": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
        "cursor": next_cursor,
        "regions_processed": batch,
        "event_count": len(merged),
        "events": merged
    }
    save_json(INDEX_PATH, output)
    print(f"Indexed {len(incoming)} discovered records; central index now has {len(merged)} current events.")
    return 0

if __name__ == "__main__":
    sys.exit(main())
