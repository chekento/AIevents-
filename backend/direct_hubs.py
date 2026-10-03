from urllib.parse import urlparse
import time
import requests
from bs4 import BeautifulSoup

BROWSER_UA = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/140 Safari/537.36"
HUBS = [
    "https://llmops.space/upcoming-events/",
    "https://reactor.microsoft.com/en-us/reactor/",
    "https://www.databricks.com/events",
    "https://www.snowflake.com/en/events/",
    "https://www.langchain.com/events",
    "https://site.wandb.ai/resources/events/",
    "https://community.arize.com/events",
    "https://cohere.com/events",
    "https://learn.mistral.ai/public/events",
    "https://www.mongodb.com/events/mongodb-local",
    "https://app.mlops.community/",
    "https://developers.openai.com/community/meetups",
    "https://forum.openai.com/public/events",
    "https://academy.openai.com/public/events",
    "https://developers.google.com/events",
    "https://www.anthropic.com/events",
    "https://www.nvidia.com/en-us/events/",
    "https://huggingface.co/events",
    "https://www.house-of-ai.org/de/events/",
    "https://ai.hamburg/de/events",
    "https://globalai.community/events/",
    "https://aitinkerers.org/events",
    "https://home.mlops.community/public/events",
    "https://gdg.community.dev/events/",
    "https://developer.microsoft.com/en-us/reactor/events/"
]
HINTS = ("event","events","meetup","conference","summit","workshop","hackathon","calendar","details")
EXTERNAL_EVENT_HOSTS = (
    "luma.com", "lu.ma", "meetup.com", "eventbrite.com",
    "sessionize.com", "pretalx.com", "gdg.community.dev"
)
session = requests.Session()
session.headers.update({"User-Agent": BROWSER_UA, "Accept-Language": "en,de;q=0.9,*;q=0.5"})

def hub_links(url, limit=10):
    try:
        r = session.get(url, timeout=10)
        r.raise_for_status()
    except Exception as exc:
        print(" hub failed:", url, exc)
        return []
    soup = BeautifulSoup(r.content, "html.parser")
    host = (urlparse(url).hostname or "").removeprefix("www.")
    root = ".".join(host.split(".")[-2:])
    out = []
    for a in soup.find_all("a", href=True):
        href = a["href"].strip()
        if href.startswith("/"):
            href = "https://" + host + href
        if not href.startswith("https://"):
            continue
        h = (urlparse(href).hostname or "").removeprefix("www.")
        text = " ".join(a.stripped_strings).lower()
        path = urlparse(href).path.lower()
        same_network = h == host or h.endswith("." + root)
        known_event_host = any(h == d or h.endswith("." + d) for d in EXTERNAL_EVENT_HOSTS)
        eventish = any(x in path or x in text for x in HINTS)
        if (same_network or known_event_host) and eventish and href.rstrip("/") != url.rstrip("/") and href not in out:
            out.append(href)
        if len(out) >= limit:
            break
    return out

def collect(parse_page):
    links = []
    for hub in HUBS:
        print("Direct hub:", hub)
        links.extend(hub_links(hub))
        time.sleep(0.4)
    links = list(dict.fromkeys(links))[:90]
    print("Direct hub candidate links:", len(links))
    events = []
    for url in links:
        try:
            events.extend(parse_page(url, "Global"))
        except Exception as exc:
            print(" direct page failed:", url, exc)
        time.sleep(0.12)
    return events
